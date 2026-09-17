## MODIFIED Requirements

### Requirement: Log configuration fields
The agent configuration file SHALL support a `log` section that controls log output location, level, and retention.

#### Scenario: Log config with all fields
- **WHEN** `config.yaml` contains a `log` section with `dir`, `level`, and `max_age_days`
- **THEN** agent uses those values to initialize the logger

#### Scenario: Log config with missing fields
- **WHEN** agent starts without `-c` and `config.yaml` omits any field within the `log` section
- **THEN** agent applies defaults: `dir=/opt/deploy-agent/log`, `level=info`, `max_age_days=30`

#### Scenario: Log config with missing fields in portable mode
- **WHEN** agent starts with `-c <path>` and the loaded config omits `log.dir`
- **THEN** agent defaults `log.dir` to `<config-file-dir>/log`
- **AND** still applies `level=info`, `max_age_days=30` for the other omitted fields

#### Scenario: Log config entirely absent
- **WHEN** `config.yaml` does not contain a `log` section
- **THEN** agent applies all default values (including the mode-specific default directory)
- **AND** agent starts successfully without prompting or error

## ADDED Requirements

### Requirement: Explicit config file selection
The agent SHALL accept a `-c`/`--config` command-line flag that specifies the exact path of the `config.yaml` file to load.

#### Scenario: Config path given on command line
- **WHEN** agent starts with `-c <path>` (or `--config <path>`)
- **THEN** agent loads configuration from that exact file path
- **AND** agent never reads from or writes to `/opt/deploy-agent/`

#### Scenario: Resolution precedence
- **WHEN** both the `-c` flag and the `AGENT_CONFIG_DIR` environment variable are provided
- **THEN** the `-c` flag takes precedence
- **AND** when `-c` is absent, `AGENT_CONFIG_DIR/config.yaml` is used when the variable is set
- **AND** when neither is provided, `/opt/deploy-agent/conf/config.yaml` is used (legacy behavior)

#### Scenario: Relative config path
- **WHEN** the path passed to `-c` is a relative path
- **THEN** agent resolves it against the current working directory
- **AND** relative default data/log directories are anchored to the config file's directory after absolute resolution

#### Scenario: Invalid config path flag usage
- **WHEN** `-c` is passed without a value or points to an unreadable existing file
- **THEN** agent prints an error to stderr and exits non-zero without starting the HTTP server

### Requirement: Portable mode directory resolution
When the config file is selected via `-c` (portable mode), the agent SHALL anchor default runtime directories to the directory containing the config file, so that no privileged system path is required.

#### Scenario: Default data directory in portable mode
- **WHEN** the config file loaded via `-c` omits `data_dir` or leaves it empty
- **THEN** agent uses `<config-file-dir>/data` as the data directory
- **AND** creates it if it does not exist

#### Scenario: Explicit data directory always wins
- **WHEN** the config file contains a non-empty `data_dir`
- **THEN** agent uses that value in both portable and legacy mode

#### Scenario: Empty data_dir no longer resets to blank
- **WHEN** an existing config file contains no `data_dir` key
- **THEN** agent keeps the applicable default data directory
- **AND** never writes runtime files to paths relative to the process working directory because of a blank data directory

#### Scenario: Legacy mode paths unchanged
- **WHEN** agent starts without `-c`
- **THEN** default data directory remains `/opt/deploy-agent/data`
- **AND** default log directory remains `/opt/deploy-agent/log`

### Requirement: Rootless nohup deployment
The project SHALL provide a rootless deployment option that runs the agent under a non-privileged user account with nohup, alongside a Chinese-language deployment guide.

#### Scenario: Start without sudo
- **WHEN** a non-root user runs the provided start script from the deployment directory
- **THEN** the agent is started with `nohup` using `-c` pointing at the deployment directory's `config.yaml`
- **AND** stdout/stderr are appended to a file in the deployment directory
- **AND** the background process PID is recorded in a PID file in the deployment directory
- **AND** no command requires sudo or write access outside the deployment directory

#### Scenario: Stop and status
- **WHEN** the user runs the provided stop script
- **THEN** the process identified by the PID file is terminated
- **AND** when the user runs the provided status script, it reports whether the agent process is alive and its PID

#### Scenario: First-boot token retrieval in rootless mode
- **WHEN** the agent starts for the first time in portable mode
- **THEN** the generated token is printed to stdout (captured in the nohup output file) and saved to the config file
- **AND** the deployment guide documents how to extract the token and register it on the server

#### Scenario: Complete config template provided
- **WHEN** the user opens the provided `config.yaml` template
- **THEN** it contains every supported key (server_url, token, port, data_dir, cors_origin, log.dir/level/max_age_days, download.enabled/allowed_paths/max_file_size/session_ttl)
- **AND** every value that must be environment-specific has a placeholder or comment instructing the user to fill it (including cors_origin, which ships without a hardcoded customer-specific default in the template)
- **AND** directory paths point inside the user's deployment directory

#### Scenario: Boot-time autostart without systemd
- **WHEN** the target server grants the user crontab access but no sudo
- **THEN** the deployment guide documents starting the agent via a crontab `@reboot` entry
- **AND** documents `systemd --user` as an alternative when lingering is enabled

#### Scenario: Upgrade without sudo
- **WHEN** a new agent binary is placed into the deployment directory
- **THEN** the guide documents stopping the agent, replacing the binary, and starting it again while preserving the existing config, data, and log directories
