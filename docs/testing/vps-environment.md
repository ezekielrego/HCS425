# VPS environment record

Captured during development on 2026-10-19:

* Ubuntu 24.04.4 LTS, Linux 6.8.0-147-generic, x86_64
* 4 logical CPU cores, 7.8 GiB RAM, approximately 71 GiB free at inspection
* OpenJDK 21.0.12.1, `javac` 21.0.12
* Apache Maven 3.8.7
* Git 2.43.0, Graphviz 2.42.2, Xvfb already present, X11VNC 0.9.16 installed but never exposed or configured
* Apache NetBeans 31 project opening and Maven-workflow verification were completed with JDK 21

The recorded environment was used to build, run, test, and capture the submitted evidence. JavaFX dependencies are resolved by Maven; Xvfb supplied the display used for automated JavaFX execution and screenshot capture.
