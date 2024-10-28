# Keycloak Scripts

1. Configure keycloak:
    ```shell
    ./local-dev/keycloak-configurer/configure-keycloak.sh http://localhost:8888
    ```
2. If you need groups:
    ```shell
    ./local-dev/keycloak-configurer/new-group.sh my-group
    ```
3. If you need users:
    ```shell
    ./local-dev/keycloak-configurer/new-user.sh <<EOM
    {
        "username": "test",
        "password": "1234",
        "email": "test@littlehorse.io",
        "firstName": "test",
        "lastName": "test",
        "isAdmin": true
    }
    EOM
    ```
