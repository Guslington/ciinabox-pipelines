/***********************************
cloudformation DSL

performs cloudformation operations

example usage
changeSets (
  stackName: 'dev'
  region: 'ap-southeast-2',
  templateUrl: 'https://s3.amazonaws.com/mybucket/cloudformation/app/master.json',
  parameters: [
    'ENVIRONMENT_NAME' : 'dev',
  ],
  accountId: '1234567890' #the aws account Id you want the stack operation performed in
  role: 'myrole' # the role to assume from the account the pipeline is running from
)

************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-s3', version='1.11.359')

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.waiters.*
import java.text.SimpleDateFormat

def call(body) {
  def config = body
  def cf = setupCfClient(config.region, config.accountId, config.role)
  def params = []
  config.parameters.each {
    params << new Parameter().withParameterKey(it.key).withParameterValue(it.value)
  }

  Date now = new Date()
  SimpleDateFormat timestamp = new SimpleDateFormat("yyyyMMddHHmmss");

  def changeSetName = config.stackName + timestamp
  def changeSetType = (!doesStackExist(cf,config.stackName)) ? 'CREATE' : 'UPDATE'

  create(cf,changeSetName,changeSetType,config.stackName,config.templateUrl,params)
  wait(cf,changeSetName,config.stackName)
  execute(cf,changeSetName,config.stackName)

}

@NonCPS
def create(client,changeSetName,changeSetType,stackName,templateUrl,params) {
  CreateChangeSetRequest req = new CreateChangeSetRequest();
	req.withChangeSetName(changeSetName)
    .withStackName(stackName)
    .withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
    .withChangeSetType(changeSetType)
    .withParameters(params)

	if (ChangeSetType.CREATE.equals(changeSetType)) {
		if (templateUrl == null || templateUrl.isEmpty()) {
			throw new GroovyRuntimeException("No templateUrl was defined");
		}
		req.withTemplateURL(templateUrl)
	} else if (ChangeSetType.UPDATE.equals(changeSetType)) {
		if (templateUrl != null && !templateUrl.isEmpty()) {
			req.setTemplateURL(templateUrl)
		} else {
			req.setUsePreviousTemplate(true)
		}
	} else {
		throw new GroovyRuntimeException("Cannot create a CloudFormation change set without a valid change set type.")
	}

	client.createChangeSet(req)
}

@NonCPS
def execute(client,changeSetName,stackName) {
  
}

@NonCPS
def wait(client,changeSetName,stackName) {
  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new DescribeChangeSetRequest().withStackName(stack).withChangeSetName(changeSet)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waitng for change set operation to complete"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
  } catch(Exception e) {
    throw new GroovyRuntimeException("Change set ${changeSetName} for stack ${stackName} creation failed - ${e}")
  }
}
