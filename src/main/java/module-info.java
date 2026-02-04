module com.gitalpha
{
    requires transitive org.json;
    requires jdk.httpserver;
    requires java.net.http;
    requires java.sql;
    requires java.management;
    requires java.base;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    
    opens com.gitalpha to javafx.graphics;
    opens com.gitalpha.UI to javafx.graphics;
    opens com.gitalpha.UI.GitDirProjectManager to javafx.graphics;
    opens com.gitalpha.UI.GitDirTab to javafx.graphics;
    exports com.gitalpha.UI.GitDirProjectManager;

    requires java.compiler;
    requires java.rmi;
//    requires com.gitalpha;

    exports com.gitalpha.Engine;
    exports com.gitalpha.Engine.GitDirContainer;
    exports com.gitalpha.Type;
    exports com.gitalpha.UI;
    exports com.gitalpha.UI.GitDirTab;
}
