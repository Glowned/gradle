export JAVA_HOME=/opt/jdk/open-jdk-11

rm -rf test-splits/*-test-classes.properties

mkdir -p build

cat > test-splits/exclude-test-classes.properties << EOL
org.gradle.launcher.daemon.DaemonLifecycleSpec=integTest
org.gradle.launcher.SystemClassLoaderTest=integTest
org.gradle.launcher.continuous.RetryOnBuildFailureContinuousIntegrationTest=integTest
org.gradle.launcher.EnablingParallelExecutionIntegrationTest=integTest
org.gradle.launcher.cli.MaxWorkersIntegrationTest=integTest
org.gradle.launcher.ConfigurationOnDemandIntegrationTest=integTest
org.gradle.launcher.SupportedBuildJvmIntegrationTest=integTest
org.gradle.launcher.GradleNativeIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonHealthLoggingIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonExpirationIntegrationTest=integTest
org.gradle.launcher.CommandLineIntegrationSpec=integTest
org.gradle.launcher.continuous.ContinuousBuildGateIntegrationTest=integTest
org.gradle.launcher.continuous.GradleBuildContinuousIntegrationTest=integTest
org.gradle.launcher.continuous.ContinuousWorkerDaemonServiceIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonOutputToggleIntegrationTest=integTest
org.gradle.launcher.cli.NotificationsIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonStartupMessageIntegrationTest=integTest
org.gradle.launcher.continuous.DeploymentContinuousBuildIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonAuthenticationIntegrationSpec=integTest
org.gradle.launcher.daemon.DaemonReportStatusIntegrationSpec=integTest
org.gradle.launcher.continuous.BuildSessionServiceReuseContinuousIntegrationTest=integTest
org.gradle.launcher.continuous.jdk7.SymlinkContinuousIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonPriorityIntegrationTest=integTest
org.gradle.launcher.BuildEnvironmentIntegrationTest=integTest
org.gradle.launcher.continuous.BuildSrcContinuousIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonJvmSettingsIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonFeedbackIntegrationSpec=integTest
org.gradle.launcher.daemon.LocaleSupportDaemonIntegrationTest=integTest
org.gradle.launcher.GradleConfigurabilityIntegrationSpec=integTest
org.gradle.launcher.daemon.DaemonSystemPropertiesIntegrationTest=integTest
org.gradle.launcher.daemon.StoppingDaemonIntegrationSpec=integTest
org.gradle.launcher.continuous.CancellationContinuousIntegrationTest=integTest
org.gradle.launcher.daemon.DaemonInitialCommunicationFailureIntegrationSpec=integTest
org.gradle.launcher.daemon.SingleUseDaemonIntegrationTest=integTest
org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitoringIntegrationTest=integTest
org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfoIntegrationSpec=integTest
org.gradle.launcher.continuous.MultiProjectContinuousIntegrationTest=integTest
org.gradle.launcher.cli.CommandLineIntegrationLoggingSpec=integTest
org.gradle.launcher.continuous.ContinuousBuildChangeReportingIntegrationTest=integTest
EOL

#while true; do

./gradlew clean launcher:quickTest -PexcludeTestClasses=true

retcode=$?

echo "Exit: $retcode"

if [ retcode -ne 0 ]; then
    echo "Error"
    exit $retcode
fi

#done
