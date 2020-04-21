[![Build status](https://github.com/navikt/padm-2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/padm-2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Prosessering av dialogmledinger (PADM)
Repository for PADM. Application written in Kotlin used to receive dialogmeldinger from external systems, doing some validation, then pushing it to our internal systems.


## Technologies used
* Kotlin
* Ktor
* Gradle
* JDK 12
* Spek
* Jackson

#### Requirements

* JDK 12


#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t padm-2 .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 padm-2`


## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`
* Jonas Henie, `jonas.henie@nav.no`
* Mathias Hellevang, `mathias.hellevang@nav.no`

### For NAV employees
We are available at the Slack channel #veden
