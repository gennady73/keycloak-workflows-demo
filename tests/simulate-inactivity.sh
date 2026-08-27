#!/usr/bin/env bash
set -euo pipefail

# Target DB containers
DB_CONTAINER="postgres-dc-a"
DB_NAME="keycloak_dc_a"
DB_USER="keycloak"

echo "========================================================="
echo "   Keycloak Workflows Demo: Simulating Inactivity        "
echo "========================================================="
read -p "Enter target username to aged: " TARGET_USER

# 1. Fetch user ID from Keycloak database
echo "Retrieving user ID for '${TARGET_USER}'..."
USER_ID=$(docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c \
    "SELECT id FROM user_entity WHERE username = '${TARGET_USER}';")

if [ -z "$USER_ID" ]; then
    echo "Error: User '${TARGET_USER}' not found in DB '${DB_NAME}'."
    exit 1
fi
echo "Found User ID: ${USER_ID}"

# 2. Update created_timestamp in user_entity to be 250 days ago 
# (250 * 24 * 60 * 60 * 1000 millisecond timestamp offset)
OFFSET_MS=$(( 250 * 24 * 60 * 60 * 1000 ))
CURRENT_MS=$(date +%s%3N)
PAST_MS=$(( CURRENT_MS - OFFSET_MS ))

echo "Aging account creation timestamp..."
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c \
    "UPDATE user_entity SET created_timestamp = ${PAST_MS} WHERE id = '${USER_ID}';"

# 3. Set custom 'last_login_time' attribute in user_attribute to bypass active session tracks
echo "Aging user profile activity markers..."
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "
INSERT INTO user_attribute (id, name, value, user_id)
VALUES (gen_random_uuid()::text, 'last_login_time', '${PAST_MS}', '${USER_ID}')
ON CONFLICT (id) DO UPDATE SET value = '${PAST_MS}';
"

echo "---------------------------------------------------------"
echo "Success! User '${TARGET_USER}' has been aged by 250 days in ${DB_CONTAINER}."
echo "Keycloak's background Workflows task will target this account on the next sweep."
echo "---------------------------------------------------------"
