/***********************************
ciinaboxEcsTask DSL

runs a tempory build or testing task on the ciinabox cluster using the ecs-cli

example usage
ciinaboxEcsTask(
  action: 'up|down' // (required, starts or stops the task)
  composeFile: 'docker-compose.yml', // (optional, defaults to docker-compose.yml in the workspace directory)
  ecsParams: 'ecs-params.yml', // (optional, https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cmd-ecs-cli-compose-ecsparams.html)
  cluster: 'my-ecs-cluster', // (optional, defaults to the jenkins ecs cluster)
  executionRole: 'role', // (optional, defaults to the jenkins execution role)
  memory: 1024, // (optional, defaults to 1GB)
  cpu: 512, // (optional, defaults to 0.5GB)
  subnet: 'subnet-123456789', // (optional, defaults to the jenkins subnet)
  securityGroup: 'sg-123456789', // (optional, defaults to the jenkins security group)
  region: 'ap-southeast-2' // (optional, defaults to current region if in AWS)
)

************************************/

import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import com.base2.ciinabox.GetEcsContainerDetatils

def call(body) {
  def config = body
  
  if (!(config.action) || !(config.action ==~ /^(up|down)$/)){
    error("action param must be supplied with the value of `up` or `down`")
  }
  
  def id = UUID.randomUUID().toString()
  
  echo id
  
  def composeFile = config.get('composeFile', 'docker-compose.yml')
  
  def metadata = new InstanceMetadata()
  // if the node is a ec2 instance using the ec2 plugin
  def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)
    
  if (!instanceId) {
    instanceId = metadata.instanceId()
  }
  
  def region = config.get('region', metadata.region())
  
  def instance = new GetInstanceDetails(region, instanceId)
  
  def subnet = config.get('subnet', instance.subnet())
  def securityGroup = config.get('securityGroup', instance.securityGroup())
  
  def task = new GetEcsContainerDetatils(region)
  
  def instanceProfile = config.get('executionRole', task.executionRole())
  def cluster = config.get('cluster', task.cluster())
  
  def cpu = config.get('cpu', 512)
  def memory = config.get('memory', 1024)
  
  sh "ecs-cli compose --region ${region} --project-name ${id} --file ${composeFile} ${config.action} --launch-type FARGATE --cluster ${cluster}"
  
  sh "ecs-cli compose --region ${region} --project-name ${id} ps --desired-status RUNNING --cluster ${cluster} | grep -oE '[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}'"
}