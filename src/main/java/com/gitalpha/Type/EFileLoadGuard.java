package com.gitalpha.Type;

/**
 * Outcome of the file-load guards applied before a diff is computed.
 * Guards decide whether a file's diff may be produced automatically.
 */
public enum EFileLoadGuard
{
	/** No guard applies — the diff can be loaded normally */
	NONE,
	/** The file is larger than the auto-load threshold; loading requires explicit user action */
	LARGE_FILE,
	/** The file content is non-text (binary); it is never loaded */
	BINARY
}
