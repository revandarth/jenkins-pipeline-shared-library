//Following are commonly used build functions

def promotionApproval(jStage, promotedTo){
       print "at approval"
       def message = "Your Jenkins job ${env.JOB_NAME} with ##${env.BUILD_NUMBER} is waiting for your Approval to promote. [Do you want to Proceed?](${env.JOB_DISPLAY_URL})"
       coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "WAITING", message, env.DEDICATED_SLAVE)
       echo "INFO: Waiting for approval"
       input "${env.SERVICE} staged at ${promotedTo} - Proceed with ${promotedTo} Deployment?"
}

def buildDockerImage(jStage){
    
    echo "DEBUG: Here at buildDockerImage"
    
    def CONTAINER_REPO = env.IMAGE_REPO
             // props = coiEnv.getEnvProperties(env.CAE_PROJECT, env.SERVICE) // ONEIDENT-3108- Error reading properties file data using ByteArrayInputStream -Todo

            // IMAGE=props."containerhub.repo" // Todo- ONEIDENT-3108
              def IMAGE_TAG=coiUtils.getImageTag(env.BRANCH_NAME)

              echo "DEBUG: CONTAINER_REPO: ${CONTAINER_REPO}"
              echo "DEBUG: IMAGE TAG- ${IMAGE_TAG}"
              echo "DEBUG: CONTAINERHUB_URL- ${env.CONTAINERHUB_URL}"

              if(!CONTAINER_REPO || !IMAGE_TAG && !env.CONTAINERHUB_URL){
                  currentBuild.result = 'FAILURE'

                  error "ERROR: Image build information are not valid. Check following, CONTAINER_REPO: ${CONTAINER_REPO}, IMAGE_TAG: ${IMAGE_TAG}, CONTAINERHUB_URL: ${env.CONTAINERHUB_URL}"
              }else {
                  docker.withRegistry(env.CONTAINERHUB_URL, 'CONTAINER_REPO_ID'){
                      def app = docker.build "${CONTAINER_REPO}:${IMAGE_TAG}"
                      retry(5) {
                        app.push()
                      }
                  }
              }
              def message= "Newly built image is published to ECH with new tag ${IMAGE_TAG}"
              coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
              if(!env.CAE_PROJECT.equals("dataservices")){
                   coiBuild.replaceImageTag()
              }else {
                  echo "Its from dataservices, no need to replace image tag in cae.yaml"
              }
             
}

def replaceImageTag(){
    
              def defaultImageTag = sh(returnStdout: true, script: "cat cae.yaml | grep 'image:' | awk -F: '{print \$3}'").trim()
              echo "DEBUG: Existing Image tag - ${defaultImageTag}"

              def IMAGE_TAG=coiUtils.getImageTag(env.BRANCH_NAME)

              echo "INFO: Replacing image tag in cae.yaml"
              if(!defaultImageTag){
                  sh """
                    sed -i "/image:/s/\$/:$IMAGE_TAG/g" cae.yaml
                  """
              }else{
                  
                  sh """
                    sed -i "/image:/s/$defaultImageTag/$IMAGE_TAG/g" cae.yaml
                  """
              }
              def imageTagAfter = sh(returnStdout: true, script: "cat cae.yaml | grep 'image:' | awk -F: '{print \$3}'").trim()
              echo "INFO: IMAGE tag in cae.yaml file after replacement - ${imageTagAfter}"
              stash "cae.yaml"

}

def setupMavenEnv(){
    echo "DEBUG: setting up JAVA_HOME"
    env.JAVA_HOME="${tool 'JDK8'}"
    echo "DEBUG: setting up java path"
    env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
    echo "DEBUG: maven setup started"
    def rtMaven = Artifactory.newMavenBuild()
    rtMaven.tool = "Maven3.3.x"
    echo "DEBUG: Done with maven setup"
    return rtMaven
}

def getBranch(){
    echo "INFO: branch: here"
    branch = sh(returnStdout: true, script: "basename ${env.BRANCH_NAME} | cut -d'-' -f1-2").trim()
    echo "DEBUG: branch ${branch}"
    return branch
}

def rtMavenRun(rtMaven, buildInfo=false){
    echo "Running maven"
    if(buildInfo){
        echo "DEBUG: Running maven build"
        rtMaven.run pom: 'pom.xml', goals: 'clean install -DskipTests', buildInfo: buildInfo 
    }else{
        echo "DEBUG: Running maven unit tests"
        rtMaven.run pom: 'pom.xml', goals: 'test'
    }
}             

def rtMavenDeploy(jStage){

    def server = getServer()
    if(server==null){
        throw new Exception("Check artifactory server connection")
    } 
    def buildInfo = Artifactory.newBuildInfo()
    def rtMaven = setupMavenEnv()
    echo "INFO: Defining release repo and snapshotRepo in artifactory"
    rtMaven.deployer releaseRepo: env.RELEASE_REPO, snapshotRepo: env.SNAPSHOT_REPO, server: server
    echo "INFO: Maven build is started"
    rtMavenRun(rtMaven, buildInfo)
    echo "INFO: Maven build is done"
    message = " Maven Build Completed for ${env.SERVICE} and deployed to Artifactory"
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
    currentBuild.result = "SUCCESS"
}

def buildSBT(jStage) {
    
    def sbt_path = "/coi/jenkins/sbt/bin/sbt"

    echo "INFO:Running SBT Build"
    sh "${sbt_path} clean"
    sh "${sbt_path} dist"
    echo "INFO:SBT Build Completed"
    sh 'cat build.sbt | grep name | head -1 | awk \'{print $3}\' | tr -d \'"\' > outFile1'
    def name = readFile('outFile1').trim()
    sh 'cat build.sbt | grep version | head -1 | awk \'{print $3}\' | tr -d \'"\' > outFile2'
    def version = readFile('outFile2').trim()
    sh 'TZ=\'America/Los_Angeles\' date +"%Y%m%d.%H%M%S" > outFile3'
    def date = readFile('outFile3').trim()
    sh "basename ${env.BRANCH_NAME} | cut -d'-' -f1-2 > outFile4"
    def branch = readFile('outFile4').trim()
    sh "cp target/universal/${name}-${version}.zip target/universal/${name}-${version}-${branch}-SNAPSHOT-${date}.zip"
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ARTIFACTORY_ID', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
              sh """
              curl -u${env.USERNAME}:${env.PASSWORD} -T target/universal/${name}-${version}-${branch}-SNAPSHOT-${date}.zip ${env.ARTIFACTORY_SBT_URL}
              """
    }
    def message = "Build And Deploy to Artifactory Completed for ${env.SERVICE}"
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
}
def npm() {

    echo "INFO: Installing npm"
    sh 'npm install'
    echo "INFO: Running npm build"
    sh 'npm run build:publish'
    sh "npm publish --registry ${ARTIFACTORY_NPM_URL}"
    def message="NPM Packages deployed to Artifactory for ${env.SERVICE}-${BRANCH}" 
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
}

def replaceVersion(){
    cmd= 'cat pom.xml | grep version | head -1 | awk -F "<" \'{print $2}\' | awk -F ">" \'{print $2}\''
    def version = sh(returnStdout: true, script: cmd).trim()
    echo "INFO: VERSION: ${version}"
    def branch = coiBuild.getBranch()
    echo "INFO: BRANCH - ${branch}"
    if(!(env.BRANCH_NAME ==~ /(^release.*)|(^master)/) && version!=null){
            sh """
                sed -i "0,/version/s/${VERSION}/${version}-${branch}-SNAPSHOT/" pom.xml
            """
    }
}

def runUnitTests(jStage){

              server = getServer()
              if(server==null){
                    throw new Exception("Check artifactory server connection")
              }
              def rtMaven = setupMavenEnv()
              rtMavenRun(rtMaven)
              if (currentBuild.result == 'UNSTABLE') {
                message = "Build had test failures"
                error "Tests failed. Not updating image"
              }
              message = "Unit Test are Completed for ${env.SERVICE}"
              coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
}

def getServer(){
    def server = Artifactory.newServer url:env.ARTIFACTORY_URL,credentialsId:"ARTIFACTORY_ID"
    return server
}

