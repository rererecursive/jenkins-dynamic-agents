/**********
Function:
  jenkinsEc2Node()

Description:
  Adds and removes Jenkins agents/slaves.

  This is useful for connecting directly to servers via SSH to perform tasks such as software deployments.

  Multiple modes are supported. The most common use case is to launch a single agent, but it may be necessary 
  to create multiple agents in a single pipeline if a fleet of servers exist that need to be connected to.

  The 'mode' parameter controls how many agents are launched.
    - 'single' creates a single agent regardless of the amount of matching EC2 instances. Instances are sorted 
      by IP and then the first is selected.
    - 'multiple' creates an agent per EC2 instance

Example usage:
  jenkinsEc2Node(
    action:   'connect',      // Required. connect | disconnect | get-node-names
    nodeName: 'hello',        // Required if action = 'connect'. The desired name of the Jenkins agent to create.
    instanceTags: [           // Required if action = 'connect'. The tags of the EC2 instances to create agents to.
      'Name': 'my-instance',
      'Env':  'dev'
    ],
    mode:   'multiple',       // Optional. See description above. Default = 'single'
    region: 'ap-southeast-2'  // Required if action = 'connect | disconnect'
  )
**********/
// This requires the 'SSH Build Agents' plugin to be installed.
import hudson.*
import hudson.model.*
import hudson.plugins.sshslaves.*;
import hudson.plugins.sshslaves.verifiers.*
import hudson.security.*
import hudson.slaves.*
import jenkins.*
import jenkins.model.*

def call(config) {
  switch(config.action) {
    case 'connect':
      return connectEc2Node(config)
      break

    case 'disconnect':
      disconnectEc2Node(config)
      break

    case 'get-node-names':
      return getNodeNames()
      break

    default:
      println "ERROR - unknown action in jenkinsEc2Node(): '${config.action}'"
      sh "exit 1"
  }
}

def connectEc2Node(config) {
  def instanceIps = sh(script: "aws ec2 describe-instances --filters Name=tag:Name,Values=${config.nodeName} --query 'Reservations[*].Instances[*].PrivateIpAddress' --output text", returnStdout: true)
  instanceIps = instanceIps.trim().split('\n').sort()
  
  // Store state in environment variables.
  env.RUN_ID      = getRandomString(5)
  env.NODE_PREFIX = config.nodeName

  if (config.mode == 'multiple') {
    env.NODE_COUNT  = instanceIps.size()
    env.NODE_MODE   = 'multiple'
  } else {
    env.NODE_COUNT  = 1
    env.NODE_MODE   = 'single'
    instanceIps     = instanceIps[0]
  }

  println "Will add ${env.NODE_COUNT} Jenkins agents."
  instanceIps.eachWithIndex { ip, i ->
    def nodeName = getNodeName(i)
    println "Adding node: ${nodeName}"

    // This assumes the credentials have already been added in Jenkins.
    Jenkins.instance.addNode(
      new DumbSlave(
        nodeName,          // Slave name
        "Dynamic slave",   // Slave description
        "/home/ec2-user",  // Remote directory
        "1",               // Executors
        Node.Mode.NORMAL,  // Node mode
        nodeName,          // Label
        new SSHLauncher(
          ip,         // Host
          22,         // Port
          'ec2-user'  // Key name in Jenkins
        ), 
        new RetentionStrategy.Always(), 
        [new EnvironmentVariablesNodeProperty([])]
      )
    )
  }

  return env.NODE_MODE == 'multiple' ? getNodeNames() : getNodeNames()[0]
}

def disconnectEc2Node(config) {
  println "Will remove ${env.NODE_COUNT} Jenkins agents."

  env.NODE_COUNT.toInteger().times { i ->
    def nodeName = getNodeName(i)
    println "Removing node: ${nodeName}"
    def jenkinsInstance = Jenkins.getInstance().getNode(nodeName);
    Jenkins.instance.removeNode(jenkinsInstance);
  }
}

def getNodeNames() {
  def names = []

  env.NODE_COUNT.toInteger().times { i -> 
    names << getNodeName(i)
  }

  return names
}

def getNodeName(index) {
  return "${env.NODE_PREFIX}-${env.RUN_ID}-${index}"
}

@NonCPS
def getRandomString(length) {
  new Random().with {(1..length).collect {(('a'..'z')).join()[ nextInt((('a'..'z')).join().length())]}.join()}
}
