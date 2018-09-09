# Overview
This repo is developed for containerized application CI-CD process on Kubernetes and Mesos Marathon using Jenkins pipeline with multi branch workflow, This CI-CD does following tasks
 
***Build*** docker image and push to docker registry with new tag(Tag is created based on the git commit branch). The built docker image will be used for deployment in all the environment across all data centers.
 
***Deploy*** to Marathon/Aurora in different environments based on git commit branch. For example,
Commit to feature branch - will get deployed to dev environment which is dedicated to developers for their ci-cd
develop branch – will get deployed to qa across all data centers.
master/release/hotfix – will get deployed to qa, staging, and production across all data centers.( Need to discuss on this)
 
***Application Status Checks:*** it will check application heath check before moving to next deployment. For instance,  if changes are from develop branch,  it will trigger deployment on qa environment across all datacenters and check for application status, upon success status, it will go to next environment based on the project branching strategy.
 
***Exceptions*** are well handled using groovy script, its capable to send slack/email notification with exact error message.
 
***Deployment Approval Notification***  After all checks are done on yellow environment, it will send slack/email deployment approval notification for green environment. This jenkins job will wait until intended person will take an action(Accept/Decline). Ideally, we will let Jenkins job wait on Jenkins master and release Jenkins slave to perform other tasks during this waiting time.
 
 
Average time takes to execute all of these tasks - Depends on the project

Following jenkins components are used for this CI-CD flow.
 
## Jenkinsfile
 
The Jenkinsfile is essentially our Jenkins Workflow, a script, that defines the CI/CD pipeline logic for a project with steps to build/test/deploy etc. captured in various stages. And this jenkinsfile should be reside in source code parent directory.
 
Sample cperecon Jenkinsfile: https://github.comcast.com/xpcs/cperecon/blob/feature/revanth-docker/Jenkinsfile
 
## Jenkins Pipeline with Multibranch workflow
 
Jenkins multibranch workflow will enable us to maintain our CI/CD workflow logic in the project/application source code repo with no additional configuration to be maintained per branch in Jenkins.
 
- Automatic Workflow (job) creation in Jenkins per new branch in the repo
- Build specific to that child-branch and its unique scm change and build history.
- Automatic job pruning/deletion for branches deleted from the repository, according to the settings.
- Flexibility to individually configure branch properties, by overriding the parent properties whenever its required.
  
 
## Jenkins Shared Libraries
 
It will help to keep just one pipeline configuration for all your projects. The idea is that our standard pipeline configuration resides in a shared repository that will be accessed for each of your projects. Each project will then only set specific properties into its own Jenkinsfile.
 
Githup repo: https://github.comcast.com/xpcs/jenkins_shared_library
 
## Jenkins slave pool
 
The Nodes/Slaves are chosen from the master by Node-Label,  its very convenient way to use slave pool with label name, which will simply get connected to the first available slave node, which usually results in the best overall turn-around time.
 
## Todo: 
 
## Reference:
 
- Jenkins pipeline: https://jenkins.io/doc/book/pipeline/
- Jenkins Shared Library: https://jenkins.io/doc/book/pipeline/shared-libraries/
- Git branching strategy: https://www.atlassian.com/git/tutorials/comparing-workflows
- Kuberentes client(kubectl): https://kubernetes.io/docs/tasks/tools/install-kubectl/
- Kuberentes: https://kubernetes.io/blog/2018/04/30/zero-downtime-deployment-kubernetes-jenkins/
- Marathon api : https://dcos.io/docs/1.9/deploying-services/marathon-api/
- Aurora client : http://aurora.apache.org/documentation/0.7.0-incubating/client-commands/
 
