/**
* Get git SHA-1
* return short git SHA-1
*
*/

def getGitsha() {
    echo "DEBUG: Get gitsha1/commmit_id from git repo"
    commit_id = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
    echo "DEBUG: got commit_id: $commit_id"
    return commit_id
}

def checkoutSource(jStage){
              echo "INFO: Checking out the source code"
              checkout scm
              stash "workspace"
              try {
                COMMIT_ID = getGitsha()
              }catch(Exception e){
                print e.toString()
                echo "Error"
              }
              echo "COMMIT_ID: ${COMMIT_ID}"
              //  environment = coiUtils.getEnvironmentFromBranchName(env.BRANCH_NAME) // StaticMethod is not allowed - Todo
              def message = "Code Chekout Completed for ${env.SERVICE}"
              coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
}

def handleException(jStage, error, environment){ 
              def errorMessage
              def message 
              if(error=="org.jenkinsci.plugins.workflow.steps.FlowInterruptedException"){
                  // currentBuild.result = 'SUCCESS'
                  // errorMessage = "This job has been aborted by user"
                  // message="${env.SERVICE} is aborted to build at ${jStage}."
                  echo "WARNING: ${env.SERVICE} is aborted to build at ${jStage}"
                  message = "WARNING: ${env.SERVICE} jenkins job is aborted at ${jStage} by user"
                  coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "FAILURE", message, env.DEDICATED_SLAVE)
              }else{
                  errorMessage = "${env.SERVICE} Failed at ${jStage}: "+error
                  message="${env.SERVICE} is failed to build at ${jStage}. Platform team has been notified and will investigate as soon as possible"
                  coiNotify.handleException(env.OPS_SPARK_ROOM_ID, env.DEV_SPARK_ROOM_ID, jStage, message, errorMessage, env.DEDICATED_SLAVE)
              }           
//               def errorMessage = "Failed at ${jStage}: "+error
//               def message="${env.SERVICE} is failed at ${jStage} for coi-${env.CAE_PROJECT}-${environment}. Platform team has been notified and will investigate as soon as possible"
//               coiNotify.handleException(env.OPS_SPARK_ROOM_ID, env.DEV_SPARK_ROOM_ID, jStage, message, errorMessage, env.DEDICATED_SLAVE)    
}

def handleException(jStage, e){  
              def errorMessage
              def message 
              def errorString

              if(e.class == "java.lang.String"){
                  print "error string"
                  errorString = e
              }else{
                  print "error object"
                  errorString = e.toString()
              }
  
              if(errorString=="org.jenkinsci.plugins.workflow.steps.FlowInterruptedException"){
                  // currentBuild.result = 'SUCCESS'
                  // errorMessage = "This job has been aborted by user"
                  // message="${env.SERVICE} is aborted to build at ${jStage}."
                  echo "WARNING: ${env.SERVICE} is aborted to build at ${jStage}"
                  message = "WARNING: ${env.SERVICE} jenkins job is aborted at ${jStage} by user"
                  coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "FAILURE", message, env.DEDICATED_SLAVE)

              }else{
                  errorMessage = "${env.SERVICE} job Failed at ${jStage}: "+errorString
                  message="${env.SERVICE} is failed to build at ${jStage}. Platform team has been notified and will investigate as soon as possible"
                  coiNotify.handleException(env.OPS_SPARK_ROOM_ID, env.DEV_SPARK_ROOM_ID, jStage, message, errorMessage, env.DEDICATED_SLAVE)
              }
}

// we may need to discuss on this?
def getImageTag(branch) {

    def imagetag
    if (branch == "master" || branch == "develop") { // may be removed, as we will no longer be building off of master
          imagetag = sh(returnStdout: true, script: "basename ${env.BRANCH_NAME} | cut -d'-' -f1-2").trim()
      //  imagetag = sh(returnStdout: true, script: "git ls-remote -q --refs --tags | awk -F'/' '{ print \$3 }' | grep '^[0-9].[0-9].[0-9]*\$' | sort --version-sort -r| head -n1").trim()
    }
    if (branch =~ /feature/ || branch =~ /bugfix/) {
        echo "DEBUG: Entered into Feature branch condition"
        imagetag = sh(returnStdout: true, script: "basename ${env.BRANCH_NAME} | cut -d'-' -f1-2").trim()
        echo "DEBUG: Got Imagetag- ${imagetag} from feature branch"
    }
    if (branch =~ /release/) { // return the version number from a release/X.Y.Z branch
        imagetag = branch.replaceAll(/release\/(.*)/, '$1')
    }
    if (branch =~ /hotfix/) { // return the version number from a hotfix/X.Y.Z branch
        imagetag = branch.replaceAll(/hotfix\/(.*)/, '$1')
    }
    
    echo "DEBUG: Returning imagetag ${imagetag}"
    if(imagetag){
      imagetag = imagetag+"-"+env.BUILD_NUMBER
    }else{
      error "Couldn't get branch name, please verify your branch name"
    }
    
    return imagetag
    
}

def staticScanner(jStage){
    def branch = coiBuild.getBranch()
    CHECKMARX_PROJECT_NAME=env.CHECKMARX_PROJECT_NAME+env.SERVICE+"-"+branch
    echo "CHECKMARX_PROJECT_NAME: ${CHECKMARX_PROJECT_NAME}"
    CHECKMARX_REPORT_PATH=env.CHECKMARX_REPORT_PATH+env.SERVICE+"-"+branch+".pdf"
    echo "CHECKMARX_REPORT_PATH: ${CHECKMARX_REPORT_PATH}"
    coiStaticScan.staticScan(env.CHECKMARX_SERVER_URL,CHECKMARX_PROJECT_NAME,env.WORKSPACE,env.DEDICATED_SLAVE,CHECKMARX_REPORT_PATH,env.DEV_SPARK_ROOM_ID,env.CHECKMARX_PRESET,env.CHECKMARX_LOCATION_PATH_EXCLUDE,env.CHECKMARX_LOCATION_FILE_EXCLUDE)
    message="Static Scan Completed for ${env.SERVICE}-${branch}"
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
}

def replaceVersion(){
    cmd= 'cat pom.xml | grep version | head -1 | awk -F "<" \'{print $2}\' | awk -F ">" \'{print $2}\''   
    try {
    def version = sh(returnStdout: true, script: cmd).trim()  
    echo "INFO: VERSION: ${version}"
    def branch = coiBuild.getBranch()
    echo "INFO: BRANCH - ${branch}"
    if(!(env.BRANCH_NAME ==~ /(^release.*)|(^master)/) && version!=null){
            sh """
                sed -i "0,/version/s/${version}/${version}-${branch}-SNAPSHOT/" pom.xml
            """
    }
    }catch(Exception e){
        print e.toString()
    }
}
