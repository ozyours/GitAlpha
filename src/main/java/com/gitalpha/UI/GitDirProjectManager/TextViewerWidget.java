package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.FileChanges;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;

public class TextViewerWidget extends BaseWidget
{
	final private TextArea TextAreaTarget;

	private FileChanges FileChangesTarget = null;

	public TextViewerWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		TextAreaTarget = new TextArea();
		TextAreaTarget.setEditable(false);
		getChildren().add(TextAreaTarget);
	}

	public void SetFileChanges(FileChanges _FileChangesTarget)
	{
		FileChangesTarget = _FileChangesTarget;
		if (FileChangesTarget == null)
		{
			TextAreaTarget.setText("");
		}
		else
		{
			int maxNum = 0;
			for (var e : FileChangesTarget._DiffLines())
			{
				Integer o = e.oldLineNumber();
				Integer n = e.newLineNumber();
				if (o != null)
					maxNum = Math.max(maxNum, o);
				if (n != null)
					maxNum = Math.max(maxNum, n);
			}
			int width = Math.max(1, String.valueOf(maxNum).length());
			StringBuilder __TextAreaContent = new StringBuilder();
			for (var e : FileChangesTarget._DiffLines())
			{
				String oldStr = e.oldLineNumber() == null ? " ".repeat(width) : String.format("%" + width + "d", e.oldLineNumber());
				String newStr = e.newLineNumber() == null ? " ".repeat(width) : String.format("%" + width + "d", e.newLineNumber());
				__TextAreaContent.append(oldStr).append(' ').append(newStr).append(' ').append(e.prefix()).append(' ').append(e.text()).append('\n');
			}
			TextAreaTarget.setText(__TextAreaContent.toString());
		}
	}
}
