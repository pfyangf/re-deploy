package config

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

type LogConfig struct {
	Dir        string `yaml:"dir"`
	Level      string `yaml:"level"`
	MaxAgeDays int    `yaml:"max_age_days"`
}

// DownloadConfig 控制 agent 端的「从 agent 拉取文件」能力。
// AllowedPaths 是 glob 模式列表（path/filepath.Match 语义）；
// EmptyAllowed 字段在远端路径不属于任何白名单时拒绝下载，禁 `..` 路径穿越。
type DownloadConfig struct {
	Enabled      bool     `yaml:"enabled"`
	AllowedPaths []string `yaml:"allowed_paths"`
	MaxFileSize  int64    `yaml:"max_file_size"` // 字节，0 = 不限
	SessionTTL   int      `yaml:"session_ttl"`   // 秒，0 = 3600
}

type Config struct {
	ServerURL  string         `yaml:"server_url"`
	Token      string         `yaml:"token"`
	Port       int            `yaml:"port"`
	DataDir    string         `yaml:"data_dir"`
	CorsOrigin string         `yaml:"cors_origin"`
	Log        LogConfig      `yaml:"log"`
	Download   DownloadConfig `yaml:"download"`
	ConfigPath string         `yaml:"-"`
	FirstRun   bool           `yaml:"-"`
}

type configFile struct {
	ServerURL  string         `yaml:"server_url"`
	Token      string         `yaml:"token"`
	Port       int            `yaml:"port"`
	DataDir    string         `yaml:"data_dir"`
	CorsOrigin string         `yaml:"cors_origin"`
	Log        logConfigRaw   `yaml:"log"`
	Download   downloadRaw    `yaml:"download"`
}

type logConfigRaw struct {
	Dir        string `yaml:"dir"`
	Level      string `yaml:"level"`
	MaxAgeDays *int   `yaml:"max_age_days"`
}

type downloadRaw struct {
	Enabled      *bool    `yaml:"enabled"`
	AllowedPaths []string `yaml:"allowed_paths"`
	MaxFileSize  int64    `yaml:"max_file_size"`
	SessionTTL   int      `yaml:"session_ttl"`
}

const (
	defaultLogDir        = "/opt/deploy-agent/log"
	defaultLogLevel      = "info"
	defaultLogMaxAgeDays = 30
	defaultCorsOrigin    = "https://bsck.cnoic.com:50002"

	// 默认白名单：常见日志/临时目录，绝不暴露 /etc、/root 等敏感路径
	defaultDownloadEnabled      = true
	defaultDownloadAllowedPaths = "/var/log/**,/opt/*/log/**,/tmp/redeploy-**"
	defaultDownloadSessionTTL   = 3600
)

func Load() (*Config, error) {
	configDir := "/opt/deploy-agent/conf"
	if envDir := os.Getenv("AGENT_CONFIG_DIR"); envDir != "" {
		configDir = envDir
	}

	configPath := filepath.Join(configDir, "config.yaml")
	cfg := &Config{
		ConfigPath: configPath,
		Port:       9009,
		DataDir:    "/opt/deploy-agent/data",
		CorsOrigin: defaultCorsOrigin,
		Log: LogConfig{
			Dir:        defaultLogDir,
			Level:      defaultLogLevel,
			MaxAgeDays: defaultLogMaxAgeDays,
		},
		Download: DownloadConfig{
			Enabled:      defaultDownloadEnabled,
			AllowedPaths: splitAndTrim(defaultDownloadAllowedPaths),
			SessionTTL:   defaultDownloadSessionTTL,
		},
	}

	// Check if config file exists
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		// First run - generate token
		token, err := generateToken()
		if err != nil {
			return nil, fmt.Errorf("failed to generate token: %w", err)
		}
		cfg.Token = token
		cfg.FirstRun = true

		// Create config directory
		if err := os.MkdirAll(configDir, 0755); err != nil {
			return nil, fmt.Errorf("failed to create config directory: %w", err)
		}

		// Save config
		if err := cfg.Save(); err != nil {
			return nil, fmt.Errorf("failed to save config: %w", err)
		}
	} else {
		// Read existing config
		data, err := os.ReadFile(configPath)
		if err != nil {
			return nil, fmt.Errorf("failed to read config file: %w", err)
		}

		var file configFile
		if err := yaml.Unmarshal(data, &file); err != nil {
			return nil, fmt.Errorf("failed to parse config file: %w", err)
		}

		cfg.ServerURL = file.ServerURL
		cfg.Token = file.Token
		cfg.Port = file.Port
		cfg.DataDir = file.DataDir
		if file.CorsOrigin != "" {
			cfg.CorsOrigin = file.CorsOrigin
		}
		if file.Log.Dir != "" {
			cfg.Log.Dir = file.Log.Dir
		}
		if file.Log.Level != "" {
			cfg.Log.Level = file.Log.Level
		}
		if file.Log.MaxAgeDays != nil {
			cfg.Log.MaxAgeDays = *file.Log.MaxAgeDays
		}
		// Download 配置：仅当 yml 显式提供时覆盖默认
		if file.Download.Enabled != nil {
			cfg.Download.Enabled = *file.Download.Enabled
		}
		if len(file.Download.AllowedPaths) > 0 {
			cfg.Download.AllowedPaths = file.Download.AllowedPaths
		}
		if file.Download.MaxFileSize > 0 {
			cfg.Download.MaxFileSize = file.Download.MaxFileSize
		}
		if file.Download.SessionTTL > 0 {
			cfg.Download.SessionTTL = file.Download.SessionTTL
		}
	}

	// 默认白名单
	if len(cfg.Download.AllowedPaths) == 0 {
		cfg.Download.AllowedPaths = splitAndTrim(defaultDownloadAllowedPaths)
	}

	// Create data directory
	if err := os.MkdirAll(cfg.DataDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create data directory: %w", err)
	}

	// Create log directory
	if err := os.MkdirAll(cfg.Log.Dir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create log directory: %w", err)
	}

	return cfg, nil
}

func (c *Config) Save() error {
	maxAge := c.Log.MaxAgeDays
	enabled := c.Download.Enabled
	file := configFile{
		ServerURL:  c.ServerURL,
		Token:      c.Token,
		Port:       c.Port,
		DataDir:    c.DataDir,
		CorsOrigin: c.CorsOrigin,
		Log: logConfigRaw{
			Dir:        c.Log.Dir,
			Level:      c.Log.Level,
			MaxAgeDays: &maxAge,
		},
		Download: downloadRaw{
			Enabled:      &enabled,
			AllowedPaths: c.Download.AllowedPaths,
			MaxFileSize:  c.Download.MaxFileSize,
			SessionTTL:   c.Download.SessionTTL,
		},
	}

	data, err := yaml.Marshal(&file)
	if err != nil {
		return fmt.Errorf("failed to marshal config: %w", err)
	}

	return os.WriteFile(c.ConfigPath, data, 0644)
}

func generateToken() (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return hex.EncodeToString(bytes), nil
}

func splitAndTrim(csv string) []string {
	out := []string{}
	start := 0
	for i := 0; i < len(csv); i++ {
		if csv[i] == ',' {
			s := trim(csv[start:i])
			if s != "" {
				out = append(out, s)
			}
			start = i + 1
		}
	}
	s := trim(csv[start:])
	if s != "" {
		out = append(out, s)
	}
	return out
}

func trim(s string) string {
	for len(s) > 0 && (s[0] == ' ' || s[0] == '\t') {
		s = s[1:]
	}
	for len(s) > 0 && (s[len(s)-1] == ' ' || s[len(s)-1] == '\t') {
		s = s[:len(s)-1]
	}
	return s
}
