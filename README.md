# Overview
Add, connect and remove Jenkins agents from a Jenkins pipeline.

*Use case:* you may wish to run commands or perform deployments on a remote server, such as an EC2 instance. Instead of installing agents on your servers, you can execute your commands directly on the servers via SSH.

This is functionally equivalent to the `sshCommand` step from the SSH plugin.

# Examples

## Adding a single agent
```groovy
@Library(['myLibrary']) _

pipeline {
  agent 'any'

  stages {
    stage('Task') {
      agent {
        // Add a remote EC2 instance as an agent.
        // A `label` can be a function that returns a string.
        label jenkinsEc2Node(
          action:   'connect',
          nodeName: 'example',
          instanceTags: [
            'Name': 'my-instance'
          ],
          region: 'ap-southeast-2'
        )
      }
      steps {
        // Add your commands here. They will run on the specified node.
        sh 'ls -al'
        sh 'df -h'
      }
    }
  }

  post {
    always {
      // Delete the agents created during this pipeline.
      jenkinsEc2Node(action: 'disconnect')
    }
  }
}
```

## Adding multiple agents
This example involves adding agents in dynamically generated pipeline stages.

```groovy
@Library(['myLibrary']) _

pipeline {
  agent 'any'

  stages {
    // The agents must be created before they're used.
    stage('Create Agents') {
      steps {
        jenkinsEc2Node(
          action:   'connect',
          nodeName: 'example',
          instanceTags: [
            'Name': 'my-instance'
          ],
          mode:   'multiple',
          region: 'ap-southeast-2'
        )
      }
    }

    // Parallel stages with different agents.
    stage('Task') {
      steps {
        script {
          parallel({
            def stages = [:]
            def nodes = jenkinsEc2Node(action: 'get-node-names')

            nodes.each { n ->
              // Dynamically generate the parallel pipeline stages.
              stages << ["Node ${n}":
                {
                  node(n) {
                    // Add your commands here. They will run on the specified node.
                    sh 'ls -al'
                    sh 'df -h'
                  }
                }
              ]
            }
            return stages
            }()
          )
        }
      }
    }
  }

  post {
    always {
      // Delete the agents created during this pipeline.
      jenkinsEc2Node(action: 'disconnect')
    }
  }
}

```