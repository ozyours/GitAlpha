package com.gitalpha.Engine;

import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public final class RunCMDUtil
{
	private RunCMDUtil()
	{
	}

	public static Pair<Integer, String> RunCMD(File _WorkingDirectory, List<String> _Args) throws IOException, InterruptedException
	{
		var __Git_EXE = AlphaSettings.Get().GetSettingEntry(AlphaSettings.GitPathName).GetDefaultValue_AsString();

		List<String> command = new java.util.ArrayList<>();
		command.add(__Git_EXE);
		command.addAll(_Args);

		ProcessBuilder builder = new ProcessBuilder(command);
		if (_WorkingDirectory != null)
			builder.directory(_WorkingDirectory);
		// Don't merge stderr into stdout — git warnings (CRLF, etc.) go to stderr
		// and should not pollute command output. Stderr is captured separately
		// and used only when the exit code indicates failure.
		builder.redirectErrorStream(false);

		Process process = builder.start();

		// Capture stderr on a separate thread to prevent pipe deadlocks
		StringBuilder errorOutput = new StringBuilder();
		Thread errorReader = new Thread(() ->
		{
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					errorOutput.append(line).append(System.lineSeparator());
				}
			}
			catch (IOException e)
			{
				// stream closed normally
			}
		});
		errorReader.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				output.append(line).append(System.lineSeparator());
			}
		}

		errorReader.join();
		int ExitCode = process.waitFor();

		// If the command failed, include stderr in the output for error reporting
		if (ExitCode != 0 && !errorOutput.isEmpty())
		{
			output.append(errorOutput.toString());
		}

		return new Pair<>(ExitCode, output.toString());
	}
}