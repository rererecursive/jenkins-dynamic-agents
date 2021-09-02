# Examples

## Single agent
```groovy
@Library(['myLibrary']) _

pipeline {
  agent 'any'

  stages {
    stage('Task') {
      agent {
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
      jenkinsEc2Node(action: 'disconnect')
    }
  }
}
```

## Multiple agents
This particular example involves adding agents in dynamically generated pipeline stages.

```groovy
@Library(['myLibrary']) _

pipeline {
  agent 'any'

  stages {
    /* The agents must be created before they're used. */
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

    /* Parallel stages with different agents. */
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