package com.gitalpha.Type;

import java.util.List;

public record GitBranch(String Name, List<String> Namespace, boolean Remote)
{
	@Override
	public String toString()
	{
		return String.format("(Branch: %s, Namespace: %s, Remote: %b)", Name, Namespace, Remote);
	}
}
