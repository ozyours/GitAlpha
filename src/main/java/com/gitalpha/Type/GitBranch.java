package com.gitalpha.Type;

import java.util.List;

public record GitBranch(String _Name, List<String> _Namespace, boolean _Remote)
{
	@Override
	public String toString()
	{
		return String.format("(Branch: %s, Namespace: %s, Remote: %b)", _Name, _Namespace, _Remote);
	}
}
