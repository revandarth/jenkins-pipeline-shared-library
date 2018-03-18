def getEnvProperties(project, service) {
    filename = "org/coi/${project}/${service}.properties"
    echo "DEBUG: loading filename: $filename"
    env_string = libraryResource filename
    echo "DEBUG: properties for build:\n$env_string"

    Properties props = new Properties()
    props.load(new ByteArrayInputStream(env_string.getBytes()))
    return props
}
