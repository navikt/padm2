[![Build status](https://github.com/navikt/padm2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/padm2/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Prosessering av dialogmeldinger (PADM)
Repository for PADM. Application written in Kotlin used to receive dialogmeldinger from external systems, doing some validation, then pushing it to our internal systems.
More information on dialogmeldinger can be found here: https://www.nhn.no/samhandlingsplattform/veileder-for-elektronisk-meldingsutveksling/del-1--elektronisk-meldingsutveksling/elektronisk-meldingsutveksling

<img src="src/svg/dialogmeldingflyt_2021_09_14.svg" alt="Image of the flow of the padm2 application">

## Technologies used
* Kotlin
* Ktor
* Gradle
* JDK 17
* Spek
* Jackson
* Kafka

#### Requirements

* JDK 17

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
### Kafka

This application owns and produces to the following topic:

* teamsykefravr.dialogmelding

### Importing flowchart from gliffy confluence
1. Open a web browser and go the confluence site that has the gliffy diagram you want to import, example site:
https://confluence.adeo.no/display/KES/SyfoSmMottak.
2. Click on the gliffy diagram and the "Edit Digram" buttom
3. Then go to File -> Export... and choose the Gliffy File Format (The gliffy diagram, should now be downloaded to you computer)
4. Open a web browser and go to: https://app.diagrams.net/
5. Choose the "Open Existing Diagram", then choose the file that was downloaded from step 3.
6. Click on File -> Save (The diagram is now saved as a drawio format, store it in the source code)
7. Click on File -> Export as SVG...(The diagram is now saved as SVG, store it in the source code)
8. Commit and push the changes so its up to date

### Editing existing flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Open Existing Diagram", then choose the file /src/flowchart/flyttdiagram.drawio
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date

### Creating a new flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Create New diagram",
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`
##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t padm2 .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 padm2`


## Contact us
### Code/project related questions can be sent to
* John Martin Lindseth, `john.martin.lindseth@nav.no`
* June Henriksen, `june.henriksen2@nav.no`
* Erik Gunnar Jansen, `erik.gunnar.jansen@nav.no`
* Mathias Rørvik, `mathias.rorvik@nav.no`
* Anders Rognstad, `anders.rognstad@nav.no`
* Geir Arne Waagbø, `geir.arne.waagbo@nav.no`
* Audun Sørheim, `audun.sorheim@nav.no`

### For NAV employees
We are available at the Slack channel #isyfo
