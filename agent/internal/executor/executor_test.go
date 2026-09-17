package executor

import (
	"testing"
)

func TestRenderParams(t *testing.T) {
	cases := []struct {
		name    string
		command string
		params  map[string]string
		want    string
	}{
		{
			name:    "single variable",
			command: "echo build {{jenkinsBuildNumber}}",
			params:  map[string]string{"jenkinsBuildNumber": "123"},
			want:    "echo build 123",
		},
		{
			name:    "dotted built-in variables multiple occurrences",
			command: "echo {{deploy.id}} {{server.host}} {{deploy.id}}",
			params: map[string]string{
				"deploy.id":   "456",
				"server.host": "10.0.0.1",
			},
			want: "echo 456 10.0.0.1 456",
		},
		{
			name:    "unknown placeholder stays literal",
			command: "echo {{unknownVar}}",
			params:  map[string]string{"deploy.id": "1"},
			want:    "echo {{unknownVar}}",
		},
		{
			name:    "nil params leaves command untouched",
			command: "echo {{deploy.id}}",
			params:  nil,
			want:    "echo {{deploy.id}}",
		},
		{
			name:    "value with spaces is substituted literally",
			command: "echo {{server.name}}",
			params:  map[string]string{"server.name": "录 像 服 务 器"},
			want:    "echo 录 像 服 务 器",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := renderParams(tc.command, tc.params); got != tc.want {
				t.Fatalf("renderParams() = %q, want %q", got, tc.want)
			}
		})
	}
}
