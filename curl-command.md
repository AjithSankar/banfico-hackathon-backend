```
curl --location 'https://auth.obiebank-sbx.banfico.io/auth/realms/provider/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=corebank-spa' \
--data-urlencode 'client_secret=corebank-spa-password' \
--data-urlencode 'username=nivas.ganesan+aihackathonteamd@banfico.com' \
--data-urlencode 'password=V49Th7yF^K7f(pr7)vJQ' \
--data-urlencode 'grant_type=password'
```