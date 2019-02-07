# Create a secret
Create a secret in the [AWS Secrets Manager console](https://console.aws.amazon.com/secretsmanager/home?region=us-east-1#/home)
 or using the [CLI](https://docs.aws.amazon.com/cli/latest/reference/secretsmanager/index.html)
 
```
 aws secretsmanager create-secret --name production/MyAwesomeAppSecret --secret-string file://mycreds.json      
```

The secret is now created and the lambda function will be able to retrieve the secret to create JWT tokens.

# Create a policy

First you need to create an AWS IAM Role so that the lambda function can execute under that role. The role determines
the permissions that the lambda function will have. 


Create a policy first, that you will attach to the role 
* Policy name: `AccessSecretMyAwesomeAppSecret`
* Allow: `secretsmanager:GetSecretValue`
* Resource: `arn:aws:secretsmanager:*:*:secret:production/MyAwesomeAppSecret`


# Create the role

[Open the IAM console > roles](https://console.aws.amazon.com/iam/home#/roles)


Create a role  with the following configuration

* Trusted entities: AWS Lambda
* Permissions
  * AWSLambdaBasicExecutionRole
  * AccessSecretMyAwesomeAppSecret
* Role name: LambdaTestRole

# Create the Lambda function

```
./gradlew buildZip

aws lambda create-function --function-name lambdatest  \
    --runtime java8 --handler com.rubenlaguna.MyLambdaHandler::handleRequest \
    --role arn:aws:iam::<account>:role/LambdaTestRole \
    --zip-file fileb://build/distributions/MyLambdaHandler-1.0-SNAPSHOT.zip \
    --timeout 60 --memory-size 512 --environment '{"Variables":{"SECRET_ID":"production/MyAwesomeAppSecret"}}'
    
aws lambda update-function-code --function-name lambdatest \
    --zip-file fileb://build/distributions/MyLambdaHandler-1.0-SNAPSHOT.zip
 
aws lambda update-function-configuration --function-name lambdatest --timeout 120  --memory-size 512
```

# Test the function 

Use the AWS Lambda console to Test with an S3 event


# References

* [Tutorial: Storing and Retriving a Secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/tutorials_basic.html)
* [Creating a Basic Secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/manage_create-basic-secret.html)
* [Using AWS Lambda with the AWS Command Line Interface](https://docs.aws.amazon.com/lambda/latest/dg/with-userapp.html)
* [aws lambda CLI reference](https://docs.aws.amazon.com/cli/latest/reference/lambda/index.html)
* [Creating a ZIP Deployment Package for a Java Function](https://docs.aws.amazon.com/lambda/latest/dg/create-deployment-pkg-zip-java.html)
* [AWS Lambda Function Handler in Java](https://docs.aws.amazon.com/lambda/latest/dg/java-programming-model-handler-types.html)
* [AWS secretsmanager CLI](https://docs.aws.amazon.com/cli/latest/reference/secretsmanager/index.html)

