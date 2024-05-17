ThisBuild / scalaVersion := "3.4.2"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

val zio_version =         "2.1.1"
val zio_http_version =    "3.0.0-RC7"
val zio_logging_version = "2.2.4"
val zio_nio_version =     "2.0.2"

lazy val dependencies = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio"                            % zio_version,
    "dev.zio" %% "zio-streams"                    % zio_version,
    "dev.zio" %% "zio-http"                       % zio_http_version,
    "dev.zio" %% "zio-logging"                    % zio_logging_version,
    "dev.zio" %% "zio-logging-slf4j"              % zio_logging_version,
    "dev.zio" %% "zio-nio"                        % zio_nio_version,

    "org.slf4j" % "slf4j-simple" % "1.7.36",
  ),
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio-test"     % zio_version,
    "dev.zio" %% "zio-test-sbt" % zio_version,
  ).map(_ % Test),
)

lazy val commonScalacOptions = Seq(
  Compile / console / scalacOptions --= Seq(
    "-Wunused:_",
    "-Xfatal-warnings",
  ),
  Test / console / scalacOptions :=
    (Compile / console / scalacOptions).value,
)

lazy val commonSettings = commonScalacOptions ++ Seq(
  update / evictionWarningOptions := EvictionWarningOptions.empty
)

lazy val root = project
    .in(file("."))
    .settings(name := "blob-post")
    .settings(commonSettings)
    .settings(dependencies)
    .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
