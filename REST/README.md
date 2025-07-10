Snowpipe Streaming REST – cURL Tutorial with JWT
This step-by-step guide demonstrates how to stream data into Snowflake using the Snowpipe Streaming Preview REST API and a JWT generated with SnowSQL.

Table of Contents
Prerequisites
Step-by-Step Instructions
Step 0: Set Environment Variables
Step 1: Discover Ingest Host
Step 1.5: Get Scoped Token for Ingest Host
Step 2: Open the Channel
Step 3: Append a Row of Data
Step 4: Get Channel Status
Optional: Clean Up
Troubleshooting
Prerequisites
A Snowflake user configured for key-pair authentication.
Public key registered using:
ALTER USER MY_USER SET RSA_PUBLIC_KEY='<your-public-key>';
Installed tools: curl, jq (for JSON parsing), and SnowSQL.
Generate your JWT:
snowsql --private-key-path rsa_key.p8 --generate-jwt \
  -a <ACCOUNT_LOCATOR> \
  -u MY_USER
Store the JWT token securely and avoid exposing it in logs or scripts.
Step-by-Step Instructions
Step 0: Set Environment Variables
# Paste the JWT token from SnowSQL
export JWT_TOKEN="PASTE_YOUR_JWT_TOKEN_HERE"

# Configuration
# Snowflake account identifier (e.g., <ACCOUNT_LOCATOR>)
export ACCOUNT="<ACCOUNT_LOCATOR>"
export USER="MY_USER"
export DB="MY_DATABASE"
export SCHEMA="MY_SCHEMA"
export PIPE="MY_PIPE"
export CHANNEL="MY_CHANNEL"

# Replace ACCOUNT with your Account URL Host to form the control plane host
export CONTROL_HOST="ACCOUNT.snowflakecomputing.com"
Step 1: Discover Ingest Host
export INGEST_HOST=$(curl -sS -X GET \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-Snowflake-Authorization-Token-Type: KEYPAIR_JWT" \
  "https://${CONTROL_HOST}/v2/streaming/hostname")

echo "Ingest Host: $INGEST_HOST"
Step 1.5: Get Scoped Token for Ingest Host
export SCOPED_TOKEN=$(curl -sS -X POST https://$CONTROL_HOST/oauth/token \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&scope=${INGEST_HOST}")

echo "Scoped Token obtained for ingest host"
Step 2: Open the Channel
curl -sS -X PUT \
  -H "Authorization: Bearer $SCOPED_TOKEN" \
  -H "Content-Type: application/json" \
  "https://${INGEST_HOST}/v2/streaming/databases/$DB/schemas/$SCHEMA/pipes/$PIPE/channels/$CHANNEL" \
  -d '{}' | tee open_resp.json | jq .
Step 3: Append a Row of Data
3.1 Extract Continuation and Offset Tokens
export CONT_TOKEN=$(jq -r '.next_continuation_token' open_resp.json)
export OFFSET_TOKEN=$(jq -r '.channel_status.last_committed_offset_token' open_resp.json)
export NEW_OFFSET=$((OFFSET_TOKEN + 1))
3.2 Create Sample Row
export NOW_TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

cat <<EOF > rows.ndjson
{
  "id": 1,
  "c1": $RANDOM,
  "ts": "$NOW_TS"
}
EOF
3.3 Append Row
curl -sS -X POST \
  -H "Authorization: Bearer $SCOPED_TOKEN" \
  -H "Content-Type: application/x-ndjson" \
  "https://${INGEST_HOST}/v2/streaming/data/databases/$DB/schemas/$SCHEMA/pipes/$PIPE/channels/$CHANNEL/rows?continuationToken=$CONT_TOKEN&offsetToken=$NEW_OFFSET" \
  --data-binary @rows.ndjson | jq .
Note: After each append operation, you must update the continuation token from the response of the append rows call. The response contains a next_continuation_token field that should be used for the next append operation.

3.4 Verify Data Ingestion
Run the following SQL in Snowflake to confirm the row was ingested:

SELECT * FROM MY_DATABASE.MY_SCHEMA.MY_TABLE WHERE id = 1;
Step 4: Get Channel Status
curl -sS -X POST \
  -H "Authorization: Bearer $SCOPED_TOKEN" \
  -H "Content-Type: application/json" \
  "https://${INGEST_HOST}/v2/streaming/databases/$DB/schemas/$SCHEMA/pipes/$PIPE:bulk-channel-status" \
  -d "{\"channel_names\": [\"$CHANNEL\"]}" | jq ".channel_statuses.\"$CHANNEL\""
Optional: Clean Up
rm -f rows.ndjson open_resp.json
unset JWT_TOKEN SCOPED_TOKEN ACCOUNT USER DB SCHEMA PIPE CHANNEL CONTROL_HOST INGEST_HOST CONT_TOKEN OFFSET_TOKEN NEW_OFFSET NOW_TS
Troubleshooting
HTTP 401: Verify your JWT token is valid and not expired. Regenerate if needed.
HTTP 404: Check that the database, schema, pipe, and channel names are correct.
No Ingest Host: Ensure the control plane host URL is correct and accessible.
