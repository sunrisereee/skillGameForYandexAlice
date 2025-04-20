# SKILLGAME FOR YANDEX ALICE - ГАЙД

## ТРЕБОВАНИЯ
- Docker
- Токен GigaChat (https://developers.sber.ru/)
- Токен CloudPub (https://cloudpub.dev/)


## НАСТРОЙКА .env
Создайте файл .env:  
```
  API_KEY=ваш_токен_gigachat  
  DB_USER=user  
  DB_PASS=password  
  CLOUDPUB_TOKEN=ваш_cloudpub_токен
```


## ЗАПУСК
### Windows/macOS
docker-compose --env-file .env up --build


### Linux
1. Раскомментируйте в docker-compose.yml:
   network_mode: "host"
2. Запустите:
docker-compose --env-file .env up --build


## ИНТЕГРАЦИЯ С ЯНДЕКС
1. Найдите в логах CloudPub URL вида:
   https://ваш_уникальный_URL.cloudpub.dev
2. Вставьте его в Webhook URL в консоли Яндекс Диалогов

## КОМАНДЫ
- Остановить: docker-compose down
- Логи: docker-compose logs -f
- Пересборка: docker-compose build --no-cache

## ПРИМЕЧАНИЯ
1. CloudPub меняет URL при перезапуске
