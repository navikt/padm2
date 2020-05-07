[![Build status](https://github.com/navikt/padm2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/padm2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Prosessering av dialogmledinger (PADM)
Repository for PADM. Application written in Kotlin used to receive dialogmeldinger from external systems, doing some validation, then pushing it to our internal systems.
More information on dialogmeldinger can be found here: https://www.nhn.no/veileder-for-elektronisk-meldingsutveksling/del-1-elektronisk-meldingsutveksling/elektronisk-meldingsutveksling/#kap-dialogmeldinger


<img src="./src/svg/flyttdiagram.svg" alt="Image of the flow of the padm2 application">

## Technologies used
* Kotlin
* Ktor
* Gradle
* JDK 12
* Spek
* Jackson

#### Requirements

* JDK 12

### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/padm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Access to the Postgres database

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

The application uses dynamically generated user / passwords for the database.
To connect to the database one must generate user / password (which lasts for one hour)
as follows:

Use The Vault Browser CLI that is build in https://vault.adeo.no


Preprod credentials:

```
read postgresql/preprod-fss/creds/padm2-admin

```

Prod credentials:

```
read postgresql/prod-fss/creds/padm2-readonly

```

### Creating/Updating flowchart
Open a web browser and go to: https://app.diagrams.net/
to edit the curent flowchart import it from here: /src/flowchart/flyttdiagram.drawio
Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
And export it to a svg file here: /src/svg/flytdiagram.svg
Commit and push the changes so its up to date

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t padm2 .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 padm2`

### Deploy redis to dev:
Deploying redis can be done with the following command:
`kubectl apply --context dev-fss --namespace default -f redis.yaml`

### Deploy redis to prod:
Deploying redis can be done with the following command:
`kubectl apply --context prod-fss --namespace default -f redis.yaml`


## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`
* Jonas Henie, `jonas.henie@nav.no`
* Mathias Hellevang, `mathias.hellevang@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding or #isyfo
