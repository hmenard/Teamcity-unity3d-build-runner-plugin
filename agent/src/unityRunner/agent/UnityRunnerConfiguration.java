package unityRunner.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.log.Loggers;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import unityRunner.common.PluginConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: clement.dagneau
 * Date: 13/12/2011
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */


public class UnityRunnerConfiguration {
    enum Platform {
        Windows,
        Mac,
        Unsupported
    }

    final String unityExecutablePath;
    final boolean quit;
    final boolean batchMode;
    final boolean runUnitTests;

    final boolean noGraphics;
    final boolean clearBefore;
    final boolean cleanAfter;
    final boolean useCleanedLog;
    final boolean warningsAsErrors;
    final String lineListPath;
    final String projectPath;
    final String executeMethod;
    final String buildPlayer;
    final String buildPath;
    final String extraOpts;
    final String unityVersion;
    final String detectedUnityVersionPath;
    final String buildTarget;

    final Platform platform;
    final java.io.File cleanedLogPath;

    final boolean ignoreLogBefore;
    final String ignoreLogBeforeText;

    final public static String MacPlistRelativePath = "Unity.app/Contents/Info.plist";
    final public static String MacUnityExecutableRelativePath = "Unity.app/Contents/MacOS/Unity";
    final public static String WindowsUnityExecutableRelativePath = "Editor\\unity.exe";

    final static String windowsLogPath = System.getenv("LOCALAPPDATA") + "\\Unity\\Editor\\Editor.log";
    final static String macLogPath = System.getProperty("user.home") + "/Library/Logs/Unity/Editor.log";

    /**
     * construct new Unity Runner configuration
     * @param agentConfiguration current agent configuration
     * @param runnerParameters current runner parameters
     * @param agentRunningBuild the agent running the build
     */
    public UnityRunnerConfiguration(BuildAgentConfiguration agentConfiguration,
                                    Map<String, String> runnerParameters,
                                    AgentRunningBuild agentRunningBuild) {

        platform = detectPlatform(agentConfiguration);
        quit = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_QUIT);
        batchMode = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_BATCH_MODE);
        runUnitTests = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_RUN_UNIT_TESTS);

        noGraphics = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_NO_GRAPHICS);
        projectPath = FilenameUtils.separatorsToSystem(
                Parameters.getString(runnerParameters, PluginConstants.PROPERTY_PROJECT_PATH));

        unityExecutablePath = FilenameUtils.separatorsToSystem(
                Parameters.getString(runnerParameters, PluginConstants.PROPERTY_UNITY_EXECUTABLE_PATH));

        unityVersion = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_UNITY_VERSION);

        detectedUnityVersionPath = GetUnityVersionPath(agentConfiguration);

        lineListPath = FilenameUtils.separatorsToSystem(Parameters.getString(runnerParameters, PluginConstants.PROPERTY_LINELIST_PATH));
        executeMethod = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_EXECUTE_METHOD);
        buildPlayer = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_BUILD_PLAYER);
        buildPath = FilenameUtils.separatorsToSystem(
                Parameters.getString(runnerParameters, PluginConstants.PROPERTY_BUILD_PATH));
        extraOpts = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_BUILD_EXTRA);
        buildTarget = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_BUILD_TARGET);

        clearBefore = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_CLEAR_OUTPUT_BEFORE);
        cleanAfter = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_CLEAN_OUTPUT_AFTER);
        warningsAsErrors = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_WARNINGS_AS_ERRORS);

        // set cleaned log path to %temp%/cleaned-%teamcity.build.id%.log
        cleanedLogPath = new java.io.File(
                agentRunningBuild.getBuildTempDirectory(),
                String.format("cleaned-%d.log", agentRunningBuild.getBuildId()) );
        useCleanedLog = true;

        ignoreLogBefore = Parameters.getBoolean(runnerParameters, PluginConstants.PROPERTY_LOG_IGNORE);
        ignoreLogBeforeText = Parameters.getString(runnerParameters, PluginConstants.PROPERTY_LOG_IGNORE_TEXT);

    }

    /**
     * This function will return the path of unity in the following order:
     *      1a. A specific version of Unity was specified. It will try to locate that version.
     *      2b. If no unity version is specified, it will look autodetect the version to use from the unity project
     *      2. If that failed, it will default to the latest version installed on the agent
     * @param agentConfiguration
     * @return
     */
    String GetUnityVersionPath(BuildAgentConfiguration agentConfiguration)
    {
        if (isSet(unityVersion)) {
            String cachedDetectedUnityVersionPath = Parameters.getString(
                    agentConfiguration.getConfigurationParameters(),
                    "unity." + unityVersion);
            if (cachedDetectedUnityVersionPath != null)
            {
                return cachedDetectedUnityVersionPath;
            }
        }
        else
        {
            String autodetectUnityVersion = GetAutoDetectedVersion();
            String autoDetectedUnityVersionPath = Parameters.getString(
                    agentConfiguration.getConfigurationParameters(),
                    "unity." + autodetectUnityVersion);

            if (isSet(autoDetectedUnityVersionPath))
            {
                return autoDetectedUnityVersionPath;
            }

            if (isSet(autodetectUnityVersion))
            {
                Loggers.AGENT.error("Could not launch on detected unity version " + autodetectUnityVersion +  ". Fallback on latest version .");
            }
            else
            {
                Loggers.AGENT.error("Auto detection failed. Launching with latest unity version instead.");
            }
        }

        // default to use 'latest' version of unity that was previously found
        return Parameters.getString(
                agentConfiguration.getConfigurationParameters(),
                PluginConstants.CONFIGPARAM_UNITY_LATEST_VERSION);
    }

    private String GetAutoDetectedVersion()
    {
        if (isSet(projectPath))
        {
            Loggers.AGENT.error("GetAutoDetectedVersion " + projectPath);

            String projectVersionFilePath = projectPath + File.separator + "ProjectSettings" + File.separator + "ProjectVersion.txt";
            try
            {
                File projectVersionFile = new File(projectVersionFilePath);
                if (projectVersionFile.exists())
                {
                    String projectVersionString = FileUtils.readFileToString(projectVersionFile);
                    projectVersionString = projectVersionString.split(":")[1].trim();
                    projectVersionString = projectVersionString.split("[fp]")[0];
                    return projectVersionString;
                }
                else
                {
                    Loggers.AGENT.error("Cannot find project version file " + projectVersionFilePath + ".");
                }
            }
            catch (Exception e)
            {
                Loggers.AGENT.error(e);
            }
        }
        else
        {
            Loggers.AGENT.error("Project path is not set. Can't autodetect unity version.");
        }

        return null;
    }

    /**
     * get path to unity executable
     * @return path to unity executable
     */
    String getUnityPath() {
        //  if the executable path is explicit, use it without asking any question
        if (isSet(unityExecutablePath)) {
            return unityExecutablePath;
        }

        // use the detected path for this unity version
        if (isSet(detectedUnityVersionPath)) {
            return detectedUnityVersionPath;
        }

        Loggers.AGENT.error("could not find a path to Unity");
        return null;
    }

    String getUnityLogPath() {
        return getUnityLogPath(platform);
    }
    
    String getCleanedLogPath() {
        return cleanedLogPath.getPath();
    }
    
    String getInterestedLogPath() {
        if (useCleanedLog) {
            return getCleanedLogPath();
        } else {
            return getUnityLogPath();
        }
    }

    /**
     * detect the platform
     * @param agentConfiguration current configuration
     * @return Platform
     */
    static Platform detectPlatform(BuildAgentConfiguration agentConfiguration) {
        if (agentConfiguration.getSystemInfo().isWindows()) {
            return Platform.Windows;
        } else if (agentConfiguration.getSystemInfo().isMac()) {
            return Platform.Mac;
        } else {
            return Platform.Unsupported;
        }
    }

    /**
     * get path of unity log
     * @param platform platform
     * @return log path or null if not supported
     */
    static String getUnityLogPath(Platform platform) {
        switch (platform) {
            case Windows:
                return windowsLogPath;
            case Mac:
                return macLogPath;
            default:
                return null;
        }
    }


    /**
     * add location to the array only if it is non-null and non-empty
     * @param location location
     * @param locations list of locations
     */
    private static void addLocation(String location, List<String> locations) {
        if (isSet(location)) {
            locations.add(location);
        }
    }

    /**
     * get list of possible unity locations e.g /Applications or "\Program Files"
     * @param platform current platform
     * @return list of locations - may be empty
     */
    static List<String> getPossibleUnityLocations(Platform platform) {
        List<String> locations = new ArrayList<>(2);

        switch (platform) {
            case Windows:
                // Search location (64 and 32 bits)
                addLocation(System.getenv("ProgramFiles"), locations);
                addLocation(System.getenv("%programfiles% (x86)"), locations);
                addLocation(System.getenv("ProgramW6432"), locations);

            case Mac:
                // on Mac there is only one location for apps.
                addLocation("/Applications", locations);
        }

        return locations;
    }


    /**
     * test if string is set to a value
     * @param str string to test
     * @return true if str is not null or empty
     */
    private static boolean isSet(String str) {
        return str != null && !str.isEmpty();
    }
}


