package io.pivio;

import com.google.common.base.Strings;
import io.pivio.dependencies.DependenciesReader;
import io.pivio.dependencies.Dependency;
import io.pivio.metadata.Metadata;

import io.pivio.metadata.MetadataService;
import io.pivio.vcs.VcsReader;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
class Collector {

    static final String DEPENDENCIES = "software_dependencies";
    static final String VCS = "vcsroot";
    static final String LAST_COMMIT_DATE = "last_commit_date";
    static final String AUTOGENERATED = "[autogenerated]";
    static final String UNKNOWN = "UNKNOWN";
    private final Reader reader;
    private final DependenciesReader dependenciesReader;
    private final MetadataService metadataService;
    private final VcsReader vcsReader;
    private final Configuration configuration;
    private final Logger log = new Logger();

    @Autowired
    public Collector(Reader reader, DependenciesReader dependenciesReader, MetadataService metadataService, VcsReader vcsReader, Configuration configuration) {
        this.reader = reader;
        this.dependenciesReader = dependenciesReader;
        this.metadataService = metadataService;
        this.vcsReader = vcsReader;
        this.configuration = configuration;
    }

    Map<String, Object> gatherSingleFile() {
        Map<String, Object> document = readFile(configuration.getYamlFilePath());
        if (!configuration.hasOption(Configuration.SWITCH_USE_THIS_YAML_FILE) &&
                !configuration.hasOption(Configuration.SWITCH_YAML_DIR)) {
            if (document.containsKey("whitelist")) {
                ArrayList<String> whiteListPivioYaml = (ArrayList<String>) document.get("whitelist");
                if (whiteListPivioYaml.size() > 0) {
                    if (configuration.WHITELIST.length > 0) {
                        configuration.WHITELIST = mergeBlackOrWhiteLists(whiteListPivioYaml,configuration.WHITELIST);
                        document.remove("whitelist");
                    }
                    else {
                        configuration.WHITELIST = (String[]) whiteListPivioYaml.toArray();
                    }
                }

            }
            if (document.containsKey("blacklist")) {
                ArrayList<String> blackListPivioYaml = (ArrayList<String>) document.get("blacklist");
                if (blackListPivioYaml.size() > 0) {
                    if (configuration.BLACKLIST.length > 0) {
                        configuration.BLACKLIST = mergeBlackOrWhiteLists(blackListPivioYaml,configuration.BLACKLIST);
                        document.remove("blacklist");
                    }
                    else {
                        configuration.BLACKLIST = (String[]) blackListPivioYaml.toArray();
                    }
                }
            }

            Optional<Metadata> metadata = metadataService.readMetadata();
            if (metadata.isPresent()) {
                Metadata m = metadata.get();
                checkAndReplaceAutogenerated(document,"version", m.version);
                checkAndReplaceAutogenerated(document,"description", m.description);
                checkAndReplaceAutogenerated(document,"name", m.name);
            }
            else {
                checkAndReplaceAutogenerated(document, "version", UNKNOWN);
                checkAndReplaceAutogenerated(document, "description", UNKNOWN);
                checkAndReplaceAutogenerated(document, "name", UNKNOWN);
            }
            document.put(VCS, vcsReader.getVCSRoot());
            try {
                document.put(LAST_COMMIT_DATE, vcsReader.getLastCommitDate());
            } catch (Exception ignored) {
            }
            List<Dependency> dependencies = dependenciesReader.getDependencies();
            boolean hasDependenciesDeclaredInDependencyFile = !dependencies.isEmpty();
            if (hasDependenciesDeclaredInDependencyFile) {
                document.put(DEPENDENCIES, dependencies);
            } else if (!document.containsKey(DEPENDENCIES)) {
                document.put(DEPENDENCIES, new ArrayList<>());
            }

        }
        log.verboseOutput("Final result has " + document.size() + " entries.", configuration.isVerbose());
        return document;
    }

    private String[] mergeBlackOrWhiteLists(ArrayList<String> whiteListA, String[] whiteListB) {
        int lenA = whiteListA.size();
        int lenB = whiteListB.length;
        String[] result = new String[lenA+lenB];
        System.arraycopy(whiteListA.toArray(new String[lenA]),0,result,0,lenA);
        System.arraycopy(whiteListB,0,result,lenA,lenB);
        return result;
    }

    private void checkAndReplaceAutogenerated(Map<String,Object> document, String key, String newValue) {
        if (document.containsKey(key) && AUTOGENERATED.equalsIgnoreCase((String) document.get(key))) {
            if(!Strings.isNullOrEmpty(newValue)) {
                document.put(key, newValue);
            }
            else {
                document.put(key, UNKNOWN);
            }
        }
    }

    private Map<String, Object> readFile(String file) {
        Map<String, Object> document;
        try {
            document = reader.readYamlFile(file);
        } catch (FileNotFoundException fnf) {
            throw new PivioFileNotFoundException("Could not find valid config file. " + fnf.getLocalizedMessage());
        }
        return document;
    }

    // TODO: Implement real support for multiple files
    List<Map<String, Object>> gatherMultipleFiles() throws FileNotFoundException {
        List<Map<String, Object>> documents = new ArrayList<>();
        String parameter = configuration.getParameter(Configuration.SWITCH_YAML_DIR);
        try {
            Files.list(new File(parameter).toPath())
                    .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .forEach(yaml -> {
                        log.output("Reading file: " + yaml);
                        documents.add(readFile(yaml.toString()));
                    });
        } catch (Exception e) {
            log.output(e.getMessage());
        }
        return documents;
    }

}