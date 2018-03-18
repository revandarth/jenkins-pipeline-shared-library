//Following are commonly used deploy functions


/**
* setupDeployEnv
* it will setup oc client
*/

def setupOcEnv() {
    echo "DEBUG: Started setting up oc client on slave"
    def OCPATH = "/home/coijenkins/occlient/"
    echo "DUBUG: OCPATH - ${OCPATH}"
    env.PATH = "${OCPATH}:${env.PATH}"
    echo "DEBUG: PATH: ${env.PATH}"
}

/**
* renDeploy
* run the deploymnet of the service in specified project
* @param dcs - array of the datacenters (alln, rcdn)
* @param project - name of the project that is used to deploy the service
* @param service - it can an application/service name
* @param environment - Deployment environment name
*/

def runDeploy(String [] dcs, environment, jStage){
   
    def project = env.CAE_PROJECT
    def service = env.SERVICE
    def image_tag = coiUtils.getImageTag(env.BRANCH_NAME)

    echo "DEBUG: project $project"
    echo "DEBUG: datacenter $dcs"
    echo "DEBUG: service $service"
    echo "DEBUG: environment $environment"
    echo "DEBUG: tag $image_tag"


    // Failed to get list of the images from containers.cisco.com- ONEIDENT-3509

    // if (!validateTag(service, image_tag)) {
    //     throw new Exception("containers.cisco.com for service $service does not contain the specified tag $tag")
    // }

    setupOcEnv()
    def message
    for (int i = 0; i<dcs.length; i++){

        def dc = dcs[i]

        deployService(dc, project, environment, service)
           
    }
    message="${service} deployment in ${environment} is done"
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, jStage, "SUCCESSFUL", message)
    currentBuild.result = 'SUCCESS'
    // health_result = health ? testService(service, url, health, port, protocol) : true

    // if(health_result){
    //     echo "DEBUG: Service is healthy"
    // }
}
 
 /**
*
* deploy the service
* @param dc - name of the datacenter (alln, rcdn)
* @param project - name of the project that is used to deploy the service
* @param service - it can an application/service name
* @param environment - Deployment environment name
* @param image_tag, - Name of the  image tag that is used to deploy
*/

def deployService(dc, project, environment, service) {
    echo "DEBUG: project - $dc"
    echo "DEBUG: service - $project"
    echo "DEBUG: environment - $environment"
    
    setupOcEnv()

    def message
    def status
    def yamlData = yamlParser()
    def deployType=yamlData[0]
    def name=yamlData[1]
    if(!yamlData || !name){
        echo "DEBUG: Couldn't get deployment details from yaml file, please verify cae.yaml file"
        error "Couldn't get deployment details from yaml file, please verify cae.yaml file"
    }
        
    timeout(time:20, unit:'MINUTES') {
        try { 
              
            withCredentials([[$class: 'StringBinding', credentialsId: "${dc}-${project}-${environment}-token" , variable: 'CAE_LOGIN_TOKEN']]) {

                sh "oc login cae-${dc}.cisco.com --token=${env.CAE_LOGIN_TOKEN}"
                echo "DEBUG: Login is successful"
                def isExist = sh(script:"oc get ${deployType} ${name}", returnStatus:true)
                sh "oc apply -f cae.yaml"
                def cmd = "oc get pods --output=json | jq '.items[].metadata | select((.annotations.\"kubernetes.io/created-by\" | fromjson.reference.name)==\"${name}\")' | jq '.name' | tr -d '\"'"

                if(isExist==0){

                    switch(deployType) {
                        case "ReplicationController":
                            deployRC(cmd, service, project, environment, dc)
                            break
                        case "PetSet":
                            deployPetSet(cmd, service, project, environment, dc)
                            break
                        case "Deployment":
                            print "Its Deployment already taken care rolling update"
                            break
                        default:
                            print "Not defined required deployment, please verify your cae.yaml file"
                            error "Not defined required deployment, please verify your cae.yaml file"
                    }
                } else {
                    sleep 60
                    def listPods = sh(returnStdout: true, script: cmd).trim().readLines()
                    for(int i=0; i < listPods.size(); i++){
                        def pod = listPods[i]
                        if(checkPodHealth(pod)){
                            echo "${pod} is up and running "
                            status = "SUCCESS"
                            message= "${service} is deployed in ${project}-${environment} at cae ${dc} "
                        }else{
                            echo "${pod} isn't ready yet, please verify pod events"
                            message= "${service} is falied to deploy in ${project}-${environment} at cae ${dc}"
                            status = "Failed"
                            // we loose spark notification using this, its just let build fail, we need to through the error using throw new Exception()
                            error("pod failed to back up, please verify event logs")

                        }
                    }
                    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, "Deploy to ${environment}", status, message)           

                }

        }

        } catch (Exception e) {
            echo "DEBUG: caught error on upgrade."
            println e.toString()
            throw e
        }
    }
}

def testService(service, url, health, port, protocol) {
    echo "DEBUG: service: $service"
    echo "DEBUG: port: $port"
    echo "DEBUG: protocol $protocol"
    echo "DEBUG: url: $url"
    echo "DEBUG: health: $health"
    try {
        if(health =~ /health/) { //if we're really testing a health endpoint:
            assert checkAppHealth(url + health,20,15) : url + health + " is not accessible"
        } else { //just validate we get a 200 response
            assert checkAppStatus(url + health,20,15) : url + health + " is not accessible"
        }       
    } catch (AssertionError e) {
        println e.toString()
        return false
    }
    return true
}

def curlApi(url,timeout=5) {
      def cmd = "curl -i -k --max-time ${timeout} ${url} 2>/dev/null | head -n 1|cut -d\\  -f2"
      println "command: " + cmd
      output = sh(returnStdout: true, script: cmd).trim()
      echo "DEBUG: output: ${output}"
      return output == "200"
}

def checkAppStatus(url,sleeptime=5,timeout=10) {
      def status
      def n = 0
      while (n < 10) {
        n++
        status = curlApi(url, timeout)
        if(status) {
            return true
        }
        println "retry " + n
        sleep sleeptime
      }
      return false
}

def checkAppHealth(url,sleeptime=5,timeout=10) {
      def response
      def n = 0
      while (n < 10) {
        n++
        response = getResponse(url, timeout)
        if(response =~ /UP/) {
            return true
        }
        println "retry " + n
        sleep sleeptime
      }
      return false
}

def getResponse(url,timeout=5) {
      def cmd = "curl -k --max-time ${timeout} ${url} 2>/dev/null"
      println "command: " + cmd
      def output
      try {
        output = sh(returnStdout: true, script: cmd).trim()
      } catch (all) {
          echo "DEBUG: got bad response on endpoint ${url}"
      }
    //  def curl --output /dev/null --silent --head --fail https://graylog-psvc-coi-management-poc.cisco.com; then echo "Yes"; fi
      echo "DEBUG: output: ${output}"
      return output
}

/*
* Return iamge tag to insures that a tag of the service is available in the registry.
*
* @param service - application/service name (Requrires we adhere to our naming convention whereby imagename == servicename)
* @param tag - image tag
**/ 

def validateTag(service, tag, user, token) { 

    echo "DEBUG: service: ${service}"
    echo "DEBUG: tag: ${tag}"

    // containers.cisco.com  doesn't use docker's private registry api, instead they have Quay apis. Need to change this.
    def cmd = "curl -q -u ${user}:${token} https://containers.cisco.com/v2/oneidentity/${service}/tags/list 2>/dev/null | sed -e 's/[][]//g' -e 's/\"//g' -e 's/ //g' | tr '}' '\n'  | awk -F: '{print \$3}'"

    println "DEBUG: validateTag: cmd: " + cmd
    output = sh(returnStdout: true, script: cmd).trim()
    println "DEBUG: tag list for service ${service}: " + output
    def found_tag = (output =~ /${tag}/) ? true : false
    echo "DEBUG: found_tag: ${found_tag}"
    return found_tag
}

def checkPodHealth(pod, timoeouthere=10) {
      def response
      int initialDelay
      int sleeptime
      sleep 30
      def initialDelaySeconds = sh(returnStdout: true, script: "oc get po ${pod} --template '{{range \$i, \$c := .spec.containers}}{{\$c.readinessProbe.initialDelaySeconds}}{{end}}'").trim()
      if(initialDelaySeconds == "<no value>"){
          initialDelay = 60
      }else{
          initialDelay = initialDelaySeconds as Integer
      }

      print "DEBUG: initialDelay ${initialDelay}"
      sleep initialDelay

      def timeoutSeconds = sh(returnStdout: true, script: "oc get po ${pod} --template '{{range \$i, \$c := .spec.containers}}{{\$c.readinessProbe.timeoutSeconds}}{{end}}'").trim()

      if(timeoutSeconds == "<no value>"){
          sleeptime = 60
      }else{
          sleeptime = timeoutSeconds as Integer
          echo "DEBUG: Timeout Seconds: ${sleeptime}"
      }

      def n = 0
      while (n < 50) {
        n++

        def cmd = "oc describe po ${pod} | grep 'Ready:' | awk -F: '{print \$2}'"
        output = sh(returnStdout: true, script: cmd).trim()
        print "Show Ready: "+ output
        if(output == "True") {
            return true
        }
        println "retry " + n
        sleep sleeptime+7
      }
      return false
}


def deployRC(cmd, service, project, environment, dc){

    def listPods = sh(returnStdout: true, script: cmd).trim().readLines() 
    print "DEBUG: listPods - "+ listPods
    def message
    def status
    for(int i=0; i<listPods.size(); i++){
        def pod = listPods[i]
        sh "oc delete pod ${pod}"
        def checkedPod = false
        def retry = 0;
        while(!checkedPod && retry < 5) {
            retry++
            def listPods2 = sh(returnStdout: true, script: cmd).trim().readLines()
            print "List 2: "+listPods2
            for (int j=0; j < listPods2.size(); j++){
                def pod2=listPods2[j]
                if(!listPods.contains(pod2)){
                    if(checkPodHealth(pod2)){
                        echo "${pod2} is up and running"
                        message= "${service} is deployed in ${project}-${environment} at cae ${dc} "
                        status = "SUCCESS"
                    }else{
                        echo "${pod2} is failed to back up, please verify event logs"
                        message= "${service} is falied to deploy in ${project}-${environment} at cae ${dc}"
                        status = "FAILED"
                        error("pod failed to back up, please verify event logs")
                    }  
                    listPods.set(i, pod2)
                    checkedPod = true
                    break
                }
            }
            sleep 10
        }
        if(!checkedPod){
            echo "DEBUG: New pod hasn't shown up after deleting, re-tried for ${retry} times"
            error "New pod hasn't shown up after deleting, something went wrong"
        }
    }
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, "Deploy to ${environment}", status, message)
}


def deployPetSet(cmd, service, project, environment, dc){

    def listPods = sh(returnStdout: true, script: cmd).trim().readLines() 
    echo "DEBUG: List of pods : " +listPods
    def message
    def status

    for(int i=0; i<listPods.size(); i++){
        def pod = listPods[i]
        sh "oc delete pod ${pod}"
        if(checkPodHealth(pod)){
            echo "${pod} is up and running"
            message= "${service} is deployed in ${project}-${environment} at cae ${dc} "
            status = "SUCCESS"
        }else{
            echo "${pod} is failed to back up, please verify event logs"
            message= "${service} is falied to deploy in ${project}-${environment} at cae ${dc}"
            status = "FAILED"
            error("pod failed to back up, please verify event logs")
        }
    }
    coiNotify.sparkNotification(env.DEV_SPARK_ROOM_ID, "Deploy to ${environment}", status, message)
}

// following yaml parse doesn't work as expected. Leaving it here for future reference
// def parseYaml(){
//     def deployType
//     def name
//     def yamlData = readYaml file: 'cae.yaml'

//     for(int i=0; i < yamlData.size(); i++){

//         if(!yamlData[i]==null){
//             switch (yamlData[i].kind){
//                 case "PetSet":
//                     deployType="PetSet"
//                     name=yamlData[i].metadata.name
//                     break
//                 case "ReplicationController":
//                     deployType="ReplicationController"
//                     name=yamlData[i].metadata.name
//                     break
//                 case "Deployment":
//                     deployType="ReplicationController"
//                     name=yamlData[i].metadata.name
//                     break
//                 default:
//                     print "Not yet found, continue looping thru yaml file"
//                     break
//             }
//         }
//     }
//     return [deployType, name]
// }

def yamlParser(){
    def cmd = "cat cae.yaml | egrep -A2 'Deployment|PetSet|ReplicationController' | awk '{ print \$2}' | awk NF"
    def deployDetails = sh(returnStdout: true, script: cmd).trim().readLines()
    return deployDetails
}

