Below is a copy/pasteable MVP spec for the PoC.



\# Spec: Angular → Primary Spring Boot → Secondary Spring Boot with Cognito M2M Auth



\## 1. Goal



Build a minimal proof of concept showing:



1\. A local Angular UI performs a \*\*dummy login\*\*.

2\. Angular calls a local Java Spring Boot service named \*\*primary\*\*.

3\. \*\*primary\*\* calls a second local Java Spring Boot service named \*\*secondary\*\*.

4\. \*\*primary authenticates to secondary using Amazon Cognito OAuth 2.0 client credentials\*\*.

5\. \*\*secondary validates the Cognito JWT access token\*\* and returns a simple label.

6\. Angular displays the result.



The AWS-recommended shape for machine-to-machine service auth with Cognito is OAuth 2.0 \*\*client credentials grant\*\*, using a Cognito user pool, app client, resource server, and custom scopes. AWS docs describe client credentials as the M2M/non-interactive grant, and Cognito resource servers/custom scopes as the way to authorize API access with Cognito access tokens. (\[AWS Documentation]\[1]) AWS also has an official CDK sample for Cognito + API Gateway M2M using client credentials; this PoC adapts the identity pattern but keeps both Spring Boot services local instead of behind API Gateway. (\[GitHub]\[2])



\## 2. Non-goals



This MVP intentionally does \*\*not\*\* do real browser-to-backend OAuth yet.



Not in Phase 1:



\* No real Angular Cognito Hosted UI login.

\* No user pool users.

\* No refresh tokens.

\* No API Gateway.

\* No production secret manager integration.

\* No Docker requirement.

\* No database.

\* No Kubernetes/ECS.



Phase 1 proves \*\*service-to-service authorization\*\* only.



Phase 2 adds browser/user OAuth from Angular to primary.



\---



\# 3. Architecture



```text

+-------------------+

| Angular UI        |

| localhost:4200    |

|                   |

| Dummy login only  |

+---------+---------+

&#x20;         |

&#x20;         | GET /api/demo

&#x20;         | X-Demo-User: demo-user

&#x20;         v

+-------------------+

| primary JSB       |

| localhost:8081    |

|                   |

| Gets Cognito      |

| access token via  |

| client\_credentials|

+---------+---------+

&#x20;         |

&#x20;         | GET /api/label

&#x20;         | Authorization: Bearer <access\_token>

&#x20;         v

+-------------------+        +------------------------+

| secondary JSB     |        | AWS Cognito            |

| localhost:8082    |<------>| User Pool              |

|                   |  JWKS  | App Client             |

| validates JWT     |        | Resource Server/Scopes |

+-------------------+        +------------------------+

```



\## Phase 1 auth model



Angular has fake login state:



```text

loggedIn = true

username = "demo-user"

```



Angular sends a dummy header to primary:



```http

X-Demo-User: demo-user

```



primary does \*\*not\*\* trust this for real security. It is only a local demo flag.



primary obtains a Cognito access token using:



```text

grant\_type=client\_credentials

scope=secondary/read

```



secondary validates the JWT using Spring Security resource-server support and requires the Cognito scope.



\---



\# 4. Repository layout



```text

cognito-m2m-local-demo/

&#x20; README.md

&#x20; Makefile

&#x20; .env.example



&#x20; scripts/

&#x20;   deploy-cognito.sh

&#x20;   destroy-cognito.sh

&#x20;   export-cdk-outputs.sh

&#x20;   run-primary.sh

&#x20;   run-secondary.sh

&#x20;   run-ui.sh

&#x20;   test-demo.sh

&#x20;   clean.sh



&#x20; infra/

&#x20;   package.json

&#x20;   tsconfig.json

&#x20;   cdk.json

&#x20;   bin/

&#x20;     app.ts

&#x20;   lib/

&#x20;     cognito-m2m-stack.ts



&#x20; primary/

&#x20;   pom.xml

&#x20;   src/main/java/com/example/primary/PrimaryApplication.java

&#x20;   src/main/java/com/example/primary/DemoController.java

&#x20;   src/main/java/com/example/primary/SecondaryClient.java

&#x20;   src/main/java/com/example/primary/CognitoTokenClient.java

&#x20;   src/main/java/com/example/primary/CognitoTokenProperties.java

&#x20;   src/main/resources/application.yml



&#x20; secondary/

&#x20;   pom.xml

&#x20;   src/main/java/com/example/secondary/SecondaryApplication.java

&#x20;   src/main/java/com/example/secondary/LabelController.java

&#x20;   src/main/java/com/example/secondary/SecurityConfig.java

&#x20;   src/main/resources/application.yml



&#x20; ui/

&#x20;   package.json

&#x20;   angular.json

&#x20;   tsconfig.json

&#x20;   src/

&#x20;     main.ts

&#x20;     index.html

&#x20;     styles.css

&#x20;     app/

&#x20;       app.component.ts

&#x20;       app.component.html

&#x20;       app.config.ts

&#x20;       demo-api.service.ts

```



\---



\# 5. AWS Cognito design



\## Resources



The CDK stack creates:



1\. Cognito User Pool.

2\. Cognito domain.

3\. Cognito Resource Server:



&#x20;  \* Identifier: `secondary`

&#x20;  \* Scope: `read`

&#x20;  \* Full scope string emitted by Cognito: `secondary/read`

4\. Cognito app client for `primary`:



&#x20;  \* OAuth flow: client credentials only.

&#x20;  \* Allowed scopes: `secondary/read`.

&#x20;  \* Client secret enabled.



Amazon Cognito app clients cannot enable `client\_credentials` on the same app client as authorization-code or implicit grants, so Phase 2 should use a \*\*separate browser app client\*\* for Angular/user login. (\[AWS Documentation]\[3])



\## CDK outputs



The stack should output:



```text

COGNITO\_USER\_POOL\_ID

COGNITO\_REGION

COGNITO\_DOMAIN\_URL

COGNITO\_TOKEN\_URL

COGNITO\_ISSUER\_URI

COGNITO\_JWKS\_URI

COGNITO\_PRIMARY\_CLIENT\_ID

COGNITO\_PRIMARY\_CLIENT\_SECRET

COGNITO\_SCOPE

```



For local dev, `scripts/export-cdk-outputs.sh` writes these to:



```text

.env.local

```



Example:



```bash

COGNITO\_REGION=us-east-1

COGNITO\_USER\_POOL\_ID=us-east-1\_abc123

COGNITO\_DOMAIN\_URL=https://example-demo.auth.us-east-1.amazoncognito.com

COGNITO\_TOKEN\_URL=https://example-demo.auth.us-east-1.amazoncognito.com/oauth2/token

COGNITO\_ISSUER\_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1\_abc123

COGNITO\_PRIMARY\_CLIENT\_ID=...

COGNITO\_PRIMARY\_CLIENT\_SECRET=...

COGNITO\_SCOPE=secondary/read

SECONDARY\_BASE\_URL=http://localhost:8082

```



\---



\# 6. CDK project



\## `infra/package.json`



```json

{

&#x20; "name": "cognito-m2m-local-demo-infra",

&#x20; "version": "0.1.0",

&#x20; "private": true,

&#x20; "scripts": {

&#x20;   "build": "tsc",

&#x20;   "cdk": "cdk",

&#x20;   "deploy": "cdk deploy",

&#x20;   "destroy": "cdk destroy"

&#x20; },

&#x20; "dependencies": {

&#x20;   "aws-cdk-lib": "^2.0.0",

&#x20;   "constructs": "^10.0.0"

&#x20; },

&#x20; "devDependencies": {

&#x20;   "@types/node": "^20.0.0",

&#x20;   "aws-cdk": "^2.0.0",

&#x20;   "typescript": "^5.0.0"

&#x20; }

}

```



\## `infra/lib/cognito-m2m-stack.ts`



```ts

import \* as cdk from "aws-cdk-lib";

import { Construct } from "constructs";

import \* as cognito from "aws-cdk-lib/aws-cognito";



export class CognitoM2mStack extends cdk.Stack {

&#x20; constructor(scope: Construct, id: string, props?: cdk.StackProps) {

&#x20;   super(scope, id, props);



&#x20;   const userPool = new cognito.UserPool(this, "UserPool", {

&#x20;     userPoolName: "local-m2m-demo-pool",

&#x20;     selfSignUpEnabled: false,

&#x20;     removalPolicy: cdk.RemovalPolicy.DESTROY

&#x20;   });



&#x20;   const domainPrefix = `local-m2m-demo-${cdk.Names.uniqueId(this).toLowerCase()}`;



&#x20;   const domain = userPool.addDomain("Domain", {

&#x20;     cognitoDomain: {

&#x20;       domainPrefix

&#x20;     }

&#x20;   });



&#x20;   const resourceServer = userPool.addResourceServer("SecondaryResourceServer", {

&#x20;     identifier: "secondary",

&#x20;     userPoolResourceServerName: "secondary",

&#x20;     scopes: \[

&#x20;       {

&#x20;         scopeName: "read",

&#x20;         scopeDescription: "Read labels from secondary service"

&#x20;       }

&#x20;     ]

&#x20;   });



&#x20;   const primaryClient = userPool.addClient("PrimaryServiceClient", {

&#x20;     userPoolClientName: "primary-service-client",

&#x20;     generateSecret: true,

&#x20;     oAuth: {

&#x20;       flows: {

&#x20;         clientCredentials: true

&#x20;       },

&#x20;       scopes: \[

&#x20;         cognito.OAuthScope.resourceServer(resourceServer, {

&#x20;           scopeName: "read",

&#x20;           scopeDescription: "Read labels from secondary service"

&#x20;         })

&#x20;       ]

&#x20;     }

&#x20;   });



&#x20;   const region = cdk.Stack.of(this).region;

&#x20;   const issuerUri = `https://cognito-idp.${region}.amazonaws.com/${userPool.userPoolId}`;

&#x20;   const domainUrl = domain.baseUrl();

&#x20;   const tokenUrl = `${domainUrl}/oauth2/token`;



&#x20;   new cdk.CfnOutput(this, "CognitoRegion", {

&#x20;     value: region

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoUserPoolId", {

&#x20;     value: userPool.userPoolId

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoDomainUrl", {

&#x20;     value: domainUrl

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoTokenUrl", {

&#x20;     value: tokenUrl

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoIssuerUri", {

&#x20;     value: issuerUri

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoJwksUri", {

&#x20;     value: `${issuerUri}/.well-known/jwks.json`

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoPrimaryClientId", {

&#x20;     value: primaryClient.userPoolClientId

&#x20;   });



&#x20;   new cdk.CfnOutput(this, "CognitoScope", {

&#x20;     value: "secondary/read"

&#x20;   });



&#x20;   /\*\*

&#x20;    \* CDK does not expose the generated client secret as a plain output

&#x20;    \* in the high-level construct. For the PoC, fetch it with:

&#x20;    \*

&#x20;    \* aws cognito-idp describe-user-pool-client \\

&#x20;    \*   --user-pool-id <pool-id> \\

&#x20;    \*   --client-id <client-id> \\

&#x20;    \*   --query 'UserPoolClient.ClientSecret' \\

&#x20;    \*   --output text

&#x20;    \*/

&#x20; }

}

```



\## `infra/bin/app.ts`



```ts

\#!/usr/bin/env node

import \* as cdk from "aws-cdk-lib";

import { CognitoM2mStack } from "../lib/cognito-m2m-stack";



const app = new cdk.App();



new CognitoM2mStack(app, "CognitoM2mLocalDemoStack", {

&#x20; env: {

&#x20;   account: process.env.CDK\_DEFAULT\_ACCOUNT,

&#x20;   region: process.env.CDK\_DEFAULT\_REGION ?? "us-east-1"

&#x20; }

});

```



\---



\# 7. secondary service



\## Responsibility



`secondary` is the protected resource server.



It exposes:



```http

GET /api/label

```



Successful response:



```json

{

&#x20; "label": "secondary says hello",

&#x20; "service": "secondary"

}

```



\## Security



All `/api/\*\*` endpoints require:



```text

Authorization: Bearer <Cognito access token>

scope: secondary/read

```



Spring Security should validate the JWT using:



```yaml

spring.security.oauth2.resourceserver.jwt.issuer-uri

```



\## `secondary/pom.xml`



Use Spring Boot with:



\* `spring-boot-starter-web`

\* `spring-boot-starter-security`

\* `spring-boot-starter-oauth2-resource-server`

\* `spring-boot-starter-validation`

\* `spring-boot-starter-test`



\## `secondary/src/main/resources/application.yml`



```yaml

server:

&#x20; port: 8082



spring:

&#x20; application:

&#x20;   name: secondary

&#x20; security:

&#x20;   oauth2:

&#x20;     resourceserver:

&#x20;       jwt:

&#x20;         issuer-uri: ${COGNITO\_ISSUER\_URI}



logging:

&#x20; level:

&#x20;   org.springframework.security: INFO

```



\## `SecurityConfig.java`



```java

package com.example.secondary;



import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;



@Configuration

public class SecurityConfig {



&#x20;   @Bean

&#x20;   SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

&#x20;       return http

&#x20;           .authorizeHttpRequests(auth -> auth

&#x20;               .requestMatchers("/actuator/health").permitAll()

&#x20;               .requestMatchers("/api/label").hasAuthority("SCOPE\_secondary/read")

&#x20;               .anyRequest().authenticated()

&#x20;           )

&#x20;           .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

&#x20;           .build();

&#x20;   }



&#x20;   @Bean

&#x20;   JwtAuthenticationConverter jwtAuthenticationConverter() {

&#x20;       JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

&#x20;       scopes.setAuthorityPrefix("SCOPE\_");

&#x20;       scopes.setAuthoritiesClaimName("scope");



&#x20;       JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

&#x20;       converter.setJwtGrantedAuthoritiesConverter(scopes);

&#x20;       return converter;

&#x20;   }

}

```



\## `LabelController.java`



```java

package com.example.secondary;



import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RestController;



@RestController

public class LabelController {



&#x20;   @GetMapping("/api/label")

&#x20;   public Map<String, String> label() {

&#x20;       return Map.of(

&#x20;           "service", "secondary",

&#x20;           "label", "secondary says hello"

&#x20;       );

&#x20;   }

}

```



\---



\# 8. primary service



\## Responsibility



`primary` is the UI-facing local backend.



It exposes:



```http

GET /api/demo

```



It:



1\. Accepts dummy UI auth header.

2\. Gets or reuses a Cognito access token using client credentials.

3\. Calls `secondary`.

4\. Returns a combined response to Angular.



Successful response:



```json

{

&#x20; "user": "demo-user",

&#x20; "primary": "primary says hello",

&#x20; "secondaryLabel": "secondary says hello"

}

```



\## `primary/src/main/resources/application.yml`



```yaml

server:

&#x20; port: 8081



spring:

&#x20; application:

&#x20;   name: primary



demo:

&#x20; secondary-base-url: ${SECONDARY\_BASE\_URL:http://localhost:8082}

&#x20; cognito:

&#x20;   token-url: ${COGNITO\_TOKEN\_URL}

&#x20;   client-id: ${COGNITO\_PRIMARY\_CLIENT\_ID}

&#x20;   client-secret: ${COGNITO\_PRIMARY\_CLIENT\_SECRET}

&#x20;   scope: ${COGNITO\_SCOPE:secondary/read}



logging:

&#x20; level:

&#x20;   com.example.primary: INFO

```



\## Token behavior



For the PoC, primary can request a token per call.



Slightly better MVP behavior:



\* Cache token in memory.

\* Reuse until `expires\_in - 60 seconds`.

\* AWS recommends caching and reusing Cognito M2M access tokens while valid, especially as M2M usage scales. (\[Amazon Web Services, Inc.]\[4])



\## `CognitoTokenClient.java`



```java

package com.example.primary;



import java.time.Instant;

import java.util.Map;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;

import org.springframework.stereotype.Component;

import org.springframework.util.LinkedMultiValueMap;

import org.springframework.web.client.RestClient;



@Component

public class CognitoTokenClient {



&#x20;   private final CognitoTokenProperties properties;

&#x20;   private final RestClient restClient;

&#x20;   private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();



&#x20;   public CognitoTokenClient(CognitoTokenProperties properties) {

&#x20;       this.properties = properties;

&#x20;       this.restClient = RestClient.builder().build();

&#x20;   }



&#x20;   public String getAccessToken() {

&#x20;       CachedToken existing = cachedToken.get();

&#x20;       if (existing != null \&\& existing.expiresAt().isAfter(Instant.now().plusSeconds(60))) {

&#x20;           return existing.accessToken();

&#x20;       }



&#x20;       LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();

&#x20;       form.add("grant\_type", "client\_credentials");

&#x20;       form.add("client\_id", properties.clientId());

&#x20;       form.add("client\_secret", properties.clientSecret());

&#x20;       form.add("scope", properties.scope());



&#x20;       @SuppressWarnings("unchecked")

&#x20;       Map<String, Object> response = restClient.post()

&#x20;           .uri(properties.tokenUrl())

&#x20;           .contentType(MediaType.APPLICATION\_FORM\_URLENCODED)

&#x20;           .body(form)

&#x20;           .retrieve()

&#x20;           .body(Map.class);



&#x20;       if (response == null || response.get("access\_token") == null) {

&#x20;           throw new IllegalStateException("Cognito token response did not include access\_token");

&#x20;       }



&#x20;       String accessToken = response.get("access\_token").toString();

&#x20;       Number expiresIn = (Number) response.getOrDefault("expires\_in", 3600);

&#x20;       Instant expiresAt = Instant.now().plusSeconds(expiresIn.longValue());



&#x20;       cachedToken.set(new CachedToken(accessToken, expiresAt));

&#x20;       return accessToken;

&#x20;   }



&#x20;   private record CachedToken(String accessToken, Instant expiresAt) {

&#x20;   }

}

```



\## `SecondaryClient.java`



```java

package com.example.primary;



import java.util.Map;

import org.springframework.stereotype.Component;

import org.springframework.web.client.RestClient;



@Component

public class SecondaryClient {



&#x20;   private final RestClient restClient;

&#x20;   private final CognitoTokenClient tokenClient;



&#x20;   public SecondaryClient(

&#x20;       DemoProperties properties,

&#x20;       CognitoTokenClient tokenClient

&#x20;   ) {

&#x20;       this.tokenClient = tokenClient;

&#x20;       this.restClient = RestClient.builder()

&#x20;           .baseUrl(properties.secondaryBaseUrl())

&#x20;           .build();

&#x20;   }



&#x20;   public String getLabel() {

&#x20;       String token = tokenClient.getAccessToken();



&#x20;       @SuppressWarnings("unchecked")

&#x20;       Map<String, Object> response = restClient.get()

&#x20;           .uri("/api/label")

&#x20;           .headers(headers -> headers.setBearerAuth(token))

&#x20;           .retrieve()

&#x20;           .body(Map.class);



&#x20;       if (response == null || response.get("label") == null) {

&#x20;           throw new IllegalStateException("Secondary response did not include label");

&#x20;       }



&#x20;       return response.get("label").toString();

&#x20;   }

}

```



\## `DemoController.java`



```java

package com.example.primary;



import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.web.bind.annotation.RestController;



@RestController

public class DemoController {



&#x20;   private final SecondaryClient secondaryClient;



&#x20;   public DemoController(SecondaryClient secondaryClient) {

&#x20;       this.secondaryClient = secondaryClient;

&#x20;   }



&#x20;   @GetMapping("/api/demo")

&#x20;   public Map<String, String> demo(

&#x20;       @RequestHeader(name = "X-Demo-User", defaultValue = "anonymous") String demoUser

&#x20;   ) {

&#x20;       String label = secondaryClient.getLabel();



&#x20;       return Map.of(

&#x20;           "user", demoUser,

&#x20;           "primary", "primary says hello",

&#x20;           "secondaryLabel", label

&#x20;       );

&#x20;   }

}

```



\---



\# 9. Angular UI



\## Responsibility



One page.



States:



1\. Logged out.

2\. Logged in as dummy user.

3\. Demo call success.

4\. Demo call failure.



\## UI behavior



Page shows:



```text

Cognito M2M Local Demo



\[Login as demo-user]



Logged in as demo-user



\[Call primary]



Primary: primary says hello

Secondary: secondary says hello

```



\## Angular service



```ts

import { HttpClient, HttpHeaders } from "@angular/common/http";

import { Injectable } from "@angular/core";

import { Observable } from "rxjs";



export interface DemoResponse {

&#x20; user: string;

&#x20; primary: string;

&#x20; secondaryLabel: string;

}



@Injectable({ providedIn: "root" })

export class DemoApiService {

&#x20; private readonly baseUrl = "http://localhost:8081";



&#x20; constructor(private readonly http: HttpClient) {}



&#x20; callDemo(user: string): Observable<DemoResponse> {

&#x20;   const headers = new HttpHeaders({

&#x20;     "X-Demo-User": user

&#x20;   });



&#x20;   return this.http.get<DemoResponse>(`${this.baseUrl}/api/demo`, { headers });

&#x20; }

}

```



\## CORS



For Phase 1, primary must allow:



```text

Origin: http://localhost:4200

```



Minimal Spring config in primary:



```java

@Bean

WebMvcConfigurer corsConfigurer() {

&#x20;   return new WebMvcConfigurer() {

&#x20;       @Override

&#x20;       public void addCorsMappings(CorsRegistry registry) {

&#x20;           registry.addMapping("/api/\*\*")

&#x20;               .allowedOrigins("http://localhost:4200")

&#x20;               .allowedMethods("GET", "POST", "OPTIONS")

&#x20;               .allowedHeaders("\*");

&#x20;       }

&#x20;   };

}

```



\---



\# 10. Makefile



Every Makefile target delegates to a shell script so non-Make users can run the same commands directly.



```makefile

.PHONY: help install deploy destroy export-env run-primary run-secondary run-ui test-demo clean



help:

&#x09;@echo "Targets:"

&#x09;@echo "  make install        Install CDK/UI deps"

&#x09;@echo "  make deploy         Deploy Cognito stack"

&#x09;@echo "  make export-env     Write .env.local from CDK outputs"

&#x09;@echo "  make run-primary    Run primary Spring Boot service"

&#x09;@echo "  make run-secondary  Run secondary Spring Boot service"

&#x09;@echo "  make run-ui         Run Angular UI"

&#x09;@echo "  make test-demo      Smoke test primary -> secondary"

&#x09;@echo "  make destroy        Destroy Cognito stack"

&#x09;@echo "  make clean          Clean local build outputs"



install:

&#x09;./scripts/install.sh



deploy:

&#x09;./scripts/deploy-cognito.sh



export-env:

&#x09;./scripts/export-cdk-outputs.sh



run-primary:

&#x09;./scripts/run-primary.sh



run-secondary:

&#x09;./scripts/run-secondary.sh



run-ui:

&#x09;./scripts/run-ui.sh



test-demo:

&#x09;./scripts/test-demo.sh



destroy:

&#x09;./scripts/destroy-cognito.sh



clean:

&#x09;./scripts/clean.sh

```



\---



\# 11. Scripts



\## `scripts/deploy-cognito.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



cd infra

npm install

npm run build

npx cdk deploy --require-approval never

```



\## `scripts/destroy-cognito.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



cd infra

npx cdk destroy --force

```



\## `scripts/export-cdk-outputs.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



STACK\_NAME="${STACK\_NAME:-CognitoM2mLocalDemoStack}"

REGION="${AWS\_REGION:-${CDK\_DEFAULT\_REGION:-us-east-1}}"



outputs\_json="$(aws cloudformation describe-stacks \\

&#x20; --stack-name "$STACK\_NAME" \\

&#x20; --region "$REGION" \\

&#x20; --query 'Stacks\[0].Outputs' \\

&#x20; --output json)"



get\_output() {

&#x20; local key="$1"

&#x20; echo "$outputs\_json" | jq -r ".\[] | select(.OutputKey == \\"$key\\") | .OutputValue"

}



USER\_POOL\_ID="$(get\_output CognitoUserPoolId)"

CLIENT\_ID="$(get\_output CognitoPrimaryClientId)"



CLIENT\_SECRET="$(aws cognito-idp describe-user-pool-client \\

&#x20; --region "$REGION" \\

&#x20; --user-pool-id "$USER\_POOL\_ID" \\

&#x20; --client-id "$CLIENT\_ID" \\

&#x20; --query 'UserPoolClient.ClientSecret' \\

&#x20; --output text)"



cat > .env.local <<EOF

COGNITO\_REGION=$REGION

COGNITO\_USER\_POOL\_ID=$USER\_POOL\_ID

COGNITO\_DOMAIN\_URL=$(get\_output CognitoDomainUrl)

COGNITO\_TOKEN\_URL=$(get\_output CognitoTokenUrl)

COGNITO\_ISSUER\_URI=$(get\_output CognitoIssuerUri)

COGNITO\_JWKS\_URI=$(get\_output CognitoJwksUri)

COGNITO\_PRIMARY\_CLIENT\_ID=$CLIENT\_ID

COGNITO\_PRIMARY\_CLIENT\_SECRET=$CLIENT\_SECRET

COGNITO\_SCOPE=$(get\_output CognitoScope)

SECONDARY\_BASE\_URL=http://localhost:8082

EOF



echo "Wrote .env.local"

```



\## `scripts/run-primary.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



set -a

source .env.local

set +a



cd primary

./mvnw spring-boot:run

```



\## `scripts/run-secondary.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



set -a

source .env.local

set +a



cd secondary

./mvnw spring-boot:run

```



\## `scripts/run-ui.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



cd ui

npm install

npm start

```



\## `scripts/test-demo.sh`



```bash

\#!/usr/bin/env bash

set -euo pipefail



curl -sS \\

&#x20; -H 'X-Demo-User: demo-user' \\

&#x20; http://localhost:8081/api/demo | jq .

```



\---



\# 12. Local runbook



```bash

cp .env.example .env.local



make deploy

make export-env



\# Terminal 1

make run-secondary



\# Terminal 2

make run-primary



\# Terminal 3

make run-ui

```



Then open:



```text

http://localhost:4200

```



Click:



```text

Login as demo-user

Call primary

```



Expected UI result:



```text

Logged in as demo-user

Primary: primary says hello

Secondary: secondary says hello

```



Smoke test without Angular:



```bash

make test-demo

```



Expected JSON:



```json

{

&#x20; "user": "demo-user",

&#x20; "primary": "primary says hello",

&#x20; "secondaryLabel": "secondary says hello"

}

```



\---



\# 13. Acceptance criteria



\## Cognito/CDK



\* `cdk deploy` creates a Cognito user pool.

\* CDK creates a resource server named `secondary`.

\* CDK creates scope `secondary/read`.

\* CDK creates an app client for primary with client credentials enabled.

\* `.env.local` can be generated from stack outputs.



\## secondary



\* `GET /api/label` without token returns `401`.

\* `GET /api/label` with invalid token returns `401`.

\* `GET /api/label` with valid token but missing scope returns `403`.

\* `GET /api/label` with valid Cognito token and `secondary/read` scope returns label JSON.



\## primary



\* `GET /api/demo` calls Cognito token endpoint.

\* primary sends Bearer token to secondary.

\* primary returns combined JSON.

\* primary caches access token until near expiration.



\## Angular



\* User can click fake login button.

\* User can click call-primary button.

\* UI displays primary and secondary labels.

\* UI displays basic error message if backend call fails.



\---



\# 14. Phase 2: real OAuth from web to primary



Phase 2 adds proper user authentication from Angular to primary.



\## Phase 2 architecture



```text

Angular

&#x20; |

&#x20; | Authorization Code + PKCE

&#x20; v

Cognito Hosted UI

&#x20; |

&#x20; | access token / id token

&#x20; v

Angular

&#x20; |

&#x20; | Authorization: Bearer <user access token>

&#x20; v

primary

&#x20; |

&#x20; | client\_credentials token

&#x20; v

secondary

```



\## Phase 2 Cognito additions



Add a second app client:



```text

angular-browser-client

```



Settings:



\* No client secret.

\* Authorization code grant.

\* PKCE.

\* Callback URL: `http://localhost:4200/callback`

\* Logout URL: `http://localhost:4200`

\* Scopes:



&#x20; \* `openid`

&#x20; \* `email`

&#x20; \* `profile`

&#x20; \* optionally `primary/read`



Keep this separate from the M2M app client because Cognito does not allow client credentials to be enabled in the same app client as authorization-code or implicit grants. (\[AWS Documentation]\[3])



\## Phase 2 primary changes



primary becomes both:



1\. OAuth2 resource server for browser/user tokens.

2\. OAuth2 client for service-to-service tokens.



primary validates Angular’s token:



```yaml

spring:

&#x20; security:

&#x20;   oauth2:

&#x20;     resourceserver:

&#x20;       jwt:

&#x20;         issuer-uri: ${COGNITO\_ISSUER\_URI}

```



Then primary continues to call secondary with its own M2M token.



Important distinction:



```text

Angular -> primary:

&#x20; user token, represents the human/browser session



primary -> secondary:

&#x20; service token, represents primary service identity

```



Do not forward the browser user token to secondary in this PoC. Keep the service boundary explicit.



\---



\# 15. Security notes



This is a demo, but the shape is intentionally close to production:



\* Use OAuth 2.0 client credentials for service-to-service auth.

\* Use scopes for authorization.

\* Validate JWTs in the resource server.

\* Cache tokens in primary instead of requesting one for every call.

\* Use a separate app client for browser login in Phase 2.

\* Do not put Cognito client secrets in Angular.

\* Do not treat the dummy Angular login as real auth.

\* In production, store the primary client secret in AWS Secrets Manager, SSM Parameter Store, Vault, or workload identity—not `.env.local`.



The main “AWS-ish” production version would put secondary behind API Gateway or an ALB/service mesh boundary, but this PoC keeps the Spring Boot resource server local so the moving parts are obvious.



\[1]: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-client-apps.html?utm\_source=chatgpt.com "Application-specific settings with app clients"

\[2]: https://github.com/aws-samples/amazon-cognito-and-api-gateway-based-machine-to-machine-authorization-using-aws-cdk?utm\_source=chatgpt.com "aws-samples/amazon-cognito-and-api-gateway-based ..."

\[3]: https://docs.aws.amazon.com/cognito/latest/developerguide/federation-endpoints-oauth-grants.html?utm\_source=chatgpt.com "OAuth 2.0 grants - Amazon Cognito"

\[4]: https://aws.amazon.com/blogs/security/how-to-monitor-optimize-and-secure-amazon-cognito-machine-to-machine-authorization/?utm\_source=chatgpt.com "How to monitor, optimize, and secure Amazon Cognito ..."



