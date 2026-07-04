import modal

stub = modal.Stub("civer-cloud-processor")

@stub.function()
def process_device_command(command: str):
    # Lógica para procesar comandos remotos pesados
    print(f"Procesando comando: {command}")
    return {"status": "success", "result": "Comando ejecutado en la nube"}

if __name__ == "__main__":
    with stub.run():
        print(process_device_command.remote("ping"))
