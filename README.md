# Krymon

Krymon is a prototype of a simple site monitoring tool. It consists of two parts: 

* A backend which stores a list of services as a JSON file and periodically tries to establish a http connection to each of the services. If the service returns a 2XX status code, it is stored that the status check succeeded. If it did not succeed, it stores that the check failed.
* A client Android App which presents a basic create-list-delete interface. The client app can talk to configurable backend. 

The project is a gradle project with submodules for the backend and the client app. 

## Local Development Setup

You can run the backend either by running `krymon.Main` from e.g. an IDE or by running `./gradlew krymon-backend:run`. The App is most easily run from intellij/android studio.

## Building artifacts

`./gradlew build`

This will produce a runnable jar at `krymon-backend/build/libs/krymon-backend-1.0-SNAPSHOT-all.jar`, which can be run using `java -jar [file].jar`. It will also produce an apk for the client App at `krymon-app/build/outputs/apk/krymon-app-release-unsigned.apk`

## Example Workflow

In one terminal, start the backend:

```
$ ./gradlew krymon-backend:run
Starting a Gradle Daemon, 1 busy Daemon could not be reused, use --status for details
NDK is missing a "platforms" directory.
If you are using NDK, verify the ndk.dir is set to a valid NDK directory.  It is currently set to /home/anton/Android/Sdk/ndk-bundle.
If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME or local.properties to remove this warning.
:krymon-model:compileJava UP-TO-DATE
:krymon-model:processResources NO-SOURCE
:krymon-model:classes UP-TO-DATE
:krymon-model:jar UP-TO-DATE
:krymon-backend:compileJava UP-TO-DATE
:krymon-backend:processResources NO-SOURCE
:krymon-backend:classes UP-TO-DATE
:krymon-backend:runFeb 14, 2018 5:54:01 PM krymon.Krymon
INFO: Krymon listening on port 8080
```
In another terminal, you can now add & list services: 

```
# Before adding any services, the list is empty.
$ curl -s localhost:8080/service | jq .
{
  "services": []
}
# We add a service with a name and a url
$ curl -s localhost:8080/service -d '{"name":"example","url":"http://www.example.com"}'
# The URL is now present. The status is UNKNOWN until the first status check has been done.
$ curl -s localhost:8080/service | jq .
{
  "services": [
    {
      "id": "73412bc5-62a3-4d2b-b73d-4e8b03fa6a0f",
      "name": "example",
      "url": "http://www.example.com",
      "status": "UNKNOWN",
      "lastCheck": 1518631312024
    }
  ]
}
# We now add google as well. This better work!
$ curl -s localhost:8080/service -d '{"name":"google","url":"https://www.google.com"}'
# The service list now includes both the added services
$ curl -s localhost:8080/service | jq .
{
  "services": [
    {
      "id": "73412bc5-62a3-4d2b-b73d-4e8b03fa6a0f",
      "name": "example",
      "url": "http://www.example.com",
      "status": "UNKNOWN",
      "lastCheck": 1518631312024
    },
    {
      "id": "064ac775-3d54-41a5-bef3-220013126ab3",
      "name": "google",
      "url": "https://www.google.com",
      "status": "UNKNOWN",
      "lastCheck": 1518631323606
    }
  ]
}
# After waiting for the services to be polled, they now report proper statuses.
# Wikipedia returns OK, while google returns fail, since it says 300 redirect.
$ curl -s localhost:8080/service | jq .
{
  "services": [
    {
      "id": "73412bc5-62a3-4d2b-b73d-4e8b03fa6a0f",
      "name": "example",
      "url": "http://www.example.com",
      "status": "OK",
      "lastCheck": 1518631366862
    },
    {
      "id": "064ac775-3d54-41a5-bef3-220013126ab3",
      "name": "google",
      "url": "https://www.google.com",
      "status": "FAIL",
      "lastCheck": 1518631367097
    }
  ]
}
# We can delete the services as well. 
$ curl -s -XDELETE localhost:8080/service/064ac775-3d54-41a5-bef3-220013126ab3
# Now there is only one service left.
$ curl -s localhost:8080/service | jq .
{
  "services": [
    {
      "id": "73412bc5-62a3-4d2b-b73d-4e8b03fa6a0f",
      "name": "example",
      "url": "http://www.example.com",
      "status": "OK",
      "lastCheck": 1518631366862
    }
  ]
}
```

The interaction flow on the Android App is similarly structured. At app startup, add a Krymon backend service to talk to. For example, `http://<ip-of-laptop>>:8080`. 

You can then click on that backend, at which point the App will list the services and statuses for that backend. You can add and remove backends from this list view as well.

