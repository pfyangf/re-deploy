package logging

import (
	"context"
	"log/slog"
)

type ctxKey string

const (
	ctxKeyTaskID    ctxKey = "task_id"
	ctxKeyStepIndex ctxKey = "step_index"
	ctxKeyUploadID  ctxKey = "upload_id"
	ctxKeyRequestID ctxKey = "request_id"
)

func WithTaskID(ctx context.Context, taskID string) context.Context {
	return context.WithValue(ctx, ctxKeyTaskID, taskID)
}

func WithStepIndex(ctx context.Context, stepIndex int) context.Context {
	return context.WithValue(ctx, ctxKeyStepIndex, stepIndex)
}

func WithUploadID(ctx context.Context, uploadID string) context.Context {
	return context.WithValue(ctx, ctxKeyUploadID, uploadID)
}

func WithRequestID(ctx context.Context, requestID string) context.Context {
	return context.WithValue(ctx, ctxKeyRequestID, requestID)
}

func FromContext(ctx context.Context) *slog.Logger {
	logger := slog.Default()
	if ctx == nil {
		return logger
	}
	attrs := make([]any, 0, 8)
	if v, ok := ctx.Value(ctxKeyTaskID).(string); ok && v != "" {
		attrs = append(attrs, "task_id", v)
	}
	if v, ok := ctx.Value(ctxKeyStepIndex).(int); ok {
		attrs = append(attrs, "step_index", v)
	}
	if v, ok := ctx.Value(ctxKeyUploadID).(string); ok && v != "" {
		attrs = append(attrs, "upload_id", v)
	}
	if v, ok := ctx.Value(ctxKeyRequestID).(string); ok && v != "" {
		attrs = append(attrs, "request_id", v)
	}
	if len(attrs) == 0 {
		return logger
	}
	return logger.With(attrs...)
}

func RequestIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if v, ok := ctx.Value(ctxKeyRequestID).(string); ok {
		return v
	}
	return ""
}
