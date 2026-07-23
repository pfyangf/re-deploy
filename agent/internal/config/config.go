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

type Config struct {
	ServerURL  string    `yaml:"server_url"`
	Token      string    `yaml:"token"`
	Port       int       `yaml:"port"`
	DataDir    string    `yaml:"data_dir"`
	Log        LogConfig `yaml:"log"`
	ConfigPath string    `yaml:"-"`
	FirstRun   bool      `yaml:"-"`
}

type configFile struct {
	ServerURL string       `yaml:"server_url"`
	Token     string       `yaml:"token"`
	Port      int          `yaml:"port"`
	DataDir   string       `yaml:"data_dir"`
	Log       logConfigRaw `yaml:"log"`
}

type logConfigRaw struct {
	Dir        string `yaml:"dir"`
	Level      string `yaml:"level"`
	MaxAgeDays *int   `yaml:"max_age_days"`
}

const (
	defaultLogDir        = "/opt/deploy-agent/log"
	defaultLogLevel      = "info"
	defaultLogMaxAgeDays = 30
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
		Log: LogConfig{
			Dir:        defaultLogDir,
			Level:      defaultLogLevel,
			MaxAgeDays: defaultLogMaxAgeDays,
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
		if file.Log.Dir != "" {
			cfg.Log.Dir = file.Log.Dir
		}
		if file.Log.Level != "" {
			cfg.Log.Level = file.Log.Level
		}
		if file.Log.MaxAgeDays != nil {
			cfg.Log.MaxAgeDays = *file.Log.MaxAgeDays
		}
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
	file := configFile{
		ServerURL: c.ServerURL,
		Token:     c.Token,
		Port:      c.Port,
		DataDir:   c.DataDir,
		Log: logConfigRaw{
			Dir:        c.Log.Dir,
			Level:      c.Log.Level,
			MaxAgeDays: &maxAge,
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
