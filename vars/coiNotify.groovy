//Following are the functions to notify build status to communication chennal Cisco Spark and bitbucket

/**
 * build notification to Bitbucket
 * return true if notification has been sent successuflly with status code 200
 * @param build status
 * @param git COMMIT_ID
 */

import groovy.json.JsonOutput

def buildNotification(STATUS,COMMIT_ID) {
  def output
  def build = JsonOutput.toJson([state : "${STATUS}",
                               key : "${env.BUILD_TAG}",
                               name : "${env.JOB_NAME}",
                               url : "${env.BUILD_URL}",
                               description : "BUILD STATUS FROM Jenkins"])
try {
  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'BITBUCKET_ID',
  usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
  output = sh(returnStdout: true, script:  "curl --max-time 3 -u${env.USERNAME}:${env.PASSWORD} -H 'Content-Type: application/json' -i -k POST 'https://bitbucket-eng-sjc1.cisco.com/bitbucket/rest/build-status/1.0/commits/${COMMIT_ID}' -d \'${build}\' 2>/dev/null | head -n 1| cut -d' ' -f2").trim()

  echo "DEBUG: output: ${output}"
  }
  }catch(org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
      echo "ERROR: Credentials not found"
      print e.toString()
      throw e
    } catch(Exception e) {
      print e.toString()
  }
  return output == "204"
}

def bitBucketNotification(status, commit_id){
  
      if(buildNotification(status, commit_id)){
            echo "INFO: Notified build status INPROGRESS to bitbucket"
      } else {
            echo "WARN: Failed to notify build status INPROGRESS to bitbucket"
      }
}

/**
* Spark notification
* return true if notification has been sent successuflly with status code 200
* @param room - Spark room id
* @param stage - Job stage
* @param status - status of the stage
*/

def sparkNotify(room, jStage, status, message, timeout=5){
    def textMessage
    def output
    switch (jStage) {
    case "Initialize":
        textMessage = "Initiated Build Process for **${message}** [${env.BRANCH_NAME} - ${env.BUILD_NUMBER}](${env.BUILD_URL})"
        break
    case "Checkout":
        textMessage = "***"+env.JOB_NAME + " " + env.BUILD_NUMBER +" STATUS**"+ "\n" + "##"+jStage+" STAGE: "+"`"+status+"`"+ "\n"+"**"
        break
    case "Job":
        textMessage=  "${env.BUILD_NUMBER}-${env.BRANCH_NAME}: "+"##"+jStage+" STAGE: "+"`"+status+"`"+"  "+"[Click here for more details: ]("+env.BUILD_URL+")"
        break
    default:
        textMessage =  "${env.BUILD_NUMBER}-${env.BRANCH_NAME}: "+"##"+jStage+" STAGE: "+"`"+status+"`"+ "\n"+"**" +"  " + message+"  "+"[Click here for more details: ]("+env.BUILD_URL+")"
        break
  }

    try {
        withCredentials([[$class: 'StringBinding', credentialsId: 'SPARK_ID', variable: 'SPARK_API_TOKEN']]) {
            output = sh(returnStdout: true, script: "curl --max-time ${timeout} https://api.ciscospark.com/v1/messages -i -k POST -H 'Authorization:Bearer ${env.SPARK_API_TOKEN}' -F 'roomId=${room}' -F 'markdown= ${textMessage}' 2>/dev/null | head -n 1 | cut -d' ' -f2").trim()
            echo "DEBUG: output: ${output}"
        }
    } catch(org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {

      echo "ERROR: Spark credentials ID is not found"
      print e.toString()
      throw e

    } catch(Exception e) {
      print e.toString()
    }

    return output == "100"
}

def sparkNotification(SPARK_ROOM_ID, jStage, status, message, slave){
  node(slave){
              if(sparkNotify(SPARK_ROOM_ID, jStage, status, message, 3)){
                echo "INFO: ${jStage} - Spark notification is sent"
              }else{
                echo "WARN: ${jStage} - Failed to send Spark Notification "
              }
  }          
}

def sparkNotification(SPARK_ROOM_ID, jStage, status, message){
              if(sparkNotify(SPARK_ROOM_ID, jStage, status, message, 3)){
                echo "INFO: ${jStage} - Spark notification is sent"
              }else{
                echo "WARN: ${jStage} - Failed to send Spark Notification "
              }
}

def handleException(OPS_SPARK_ROOM_ID, DEV_SPARK_ROOM_ID, jStage, message, errorMessage, slave = "slave1"){
    node(slave){
              if(sparkNotify(DEV_SPARK_ROOM_ID, jStage, "FAILED", message, 3) && sparkNotify(OPS_SPARK_ROOM_ID, jStage, "FAILED", errorMessage, 3)){
                  echo "INFO: ${jStage} Spark notification is sent"
              }else {
                  echo "WARN: ${jStage} Failed to send spark notification"
              }
    }
} 

def handleFinally(){

  echo "DEBUG: Job is completed"
    
    // node(env.DEDICATED_SLAVE){
    //   def message
    //   jStage = "Job"
    //   def buildStatus = currentBuild.result
    //   //def commit_id = coiUtils.getGitsha()
    //   switch (buildStatus) {
    //         case "SUCCESS":
    //             message="Jenkins job built successfully."
    //          //   if(buildNotification("SUCCESSFUL",commit_id)) {
    //          //     echo "INFO: Jenkins Job success notification to bitbucket"
    //          //     }else{
    //          //       echo "WARN: Failed to send nofication to bitbucket"
    //          //     }
    //          //   sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, buildStatus, message)
    //          echo "DEBUG: ${message}"
    //             break
    //         case "FAILURE":
    //             messasge="Jenkins job failed to build"
    //             sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, buildStatus, message)             
    //             sparkNotification(env.OPS_SPARK_ROOM_ID, jStage, buildStatus, message)
    //             echo "DEBUG: ${message}"

    //             break
    //         case "UNSTABLE":
    //             messasge="Jenkins job is unstable"
    //             sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, buildStatus, message)
    //             break
    //         default:
    //             break
    //     }

    // }
}
