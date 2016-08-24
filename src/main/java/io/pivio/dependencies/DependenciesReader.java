package io.pivio.dependencies;

import io.pivio.Configuration;
import io.pivio.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class DependenciesReader {

    @Autowired
    BuildTool buildTool;

    @Autowired
    Configuration configuration;

    @Autowired
    Logger log;

    public List<Dependency> getDependencies() {
        String sourceCodeDirectory = getSourceDirectory();
        File directory = new File(sourceCodeDirectory);
        log.verboseOutput("Looking for source code in directory "+directory.getAbsolutePath()+".");
        DependencyReader dependencyReader = buildTool.getDependencyReader(directory);
        return dependencyReader.readDependencies(directory.getAbsolutePath());
    }

    String getSourceDirectory() {
        String sourceRootParameter = configuration.getParameter(Configuration.SWITCH_SOURCE_DIR);
        String sourceCodeParameter = configuration.getParameter(Configuration.SWITCH_SOURCE_CODE);
        String sourceCodeDirectory;

        if (sourceCodeParameter.startsWith("/")) {
            sourceCodeDirectory = sourceCodeParameter;
        } else {
            String slash = sourceRootParameter.endsWith("/") ? "" : "/";
            sourceCodeDirectory = sourceRootParameter + slash + sourceCodeParameter;
        }
        return sourceCodeDirectory;
    }
}
