package com.gitalpha.Engine;

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

	public static String RunCMD(File _WorkingDirectory, List<String> _Args) throws IOException, InterruptedException
	{
		var __Git_EXE = AlphaSettings.Get().GetSettingEntry(AlphaSettings.GitPathName).GetDefaultValue_AsString();

		List<String> command = new java.util.ArrayList<>();
		command.add(__Git_EXE);
		command.addAll(_Args);

		ProcessBuilder builder = new ProcessBuilder(command);
		if (_WorkingDirectory != null)
			builder.directory(_WorkingDirectory);
		builder.redirectErrorStream(true);

		Process process = builder.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				output.append(line).append(System.lineSeparator());
			}
		}

		process.waitFor();
		return output.toString();
	}
}
