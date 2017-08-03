def staticScan(CHECKMARX_SERVER_URL,CHECKMARX_PROJECT_NAME,FOLDER_PATH,SLAVE,CHECKMARX_REPORT_PATH,DEV_SPARK_ROOM,CHECKMARX_PRESET,CHECKMARX_LOCATION_PATH_EXCLUDE,CHECKMARX_LOCATION_FILES_EXCLUDE){
   node(SLAVE) {
   	unstash 'workspace'
    echo "Running StaticScan"
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'CHECKMARX_ID', usernameVariable: "USERNAME", passwordVariable: 'PASSWORD']]){
    sh "/coi/jenkins/CxConsolePlugin-7.5.0-20160121-1453/runCxConsole.sh scan -v -CxServer ${CHECKMARX_SERVER_URL} -projectName ${CHECKMARX_PROJECT_NAME} -CxUser ${env.USERNAME} -CxPassword ${env.PASSWORD} -Locationtype folder -LocationPath ${env.WORKSPACE} -Preset \"${CHECKMARX_PRESET}\" -ReportPDF ${CHECKMARX_REPORT_PATH}"
    }
    def TEXT= "Checkmax Static Scan Report for: " +  env.BUILD_URL
    withCredentials([[$class: 'StringBinding', credentialsId: 'SPARK_ID', variable: 'SPARK_API_TOKEN']]) {
    sh "curl -F \"files=@${CHECKMARX_REPORT_PATH}\" --request POST -H \"Authorization: Bearer ${env.SPARK_API_TOKEN}\" -F \"roomId=${DEV_SPARK_ROOM}\" https://api.ciscospark.com/v1/messages -F 'text=${TEXT}'" 
   }
  }
}