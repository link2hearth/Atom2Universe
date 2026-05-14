FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .

# Dossier de données persistantes (SQLite)
VOLUME ["/data"]

EXPOSE 47123

CMD ["python", "-u", "server.py"]
