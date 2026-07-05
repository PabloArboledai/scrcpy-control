import os
from PIL import Image

def resize_icon(input_path):
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }
    
    base_path = "app/src/main/res"
    
    try:
        with Image.open(input_path) as img:
            # Asegurar que sea cuadrado
            width, height = img.size
            min_dim = min(width, height)
            left = (width - min_dim) / 2
            top = (height - min_dim) / 2
            right = (width + min_dim) / 2
            bottom = (height + min_dim) / 2
            img = img.crop((left, top, right, bottom))
            
            for folder, size in densities.items():
                target_dir = os.path.join(base_path, folder)
                os.makedirs(target_dir, exist_ok=True)
                
                # Guardar ic_launcher.png
                resized = img.resize((size, size), Image.Resampling.LANCZOS)
                resized.save(os.path.join(target_dir, "ic_launcher.png"), "PNG")
                # Guardar ic_launcher_round.png
                resized.save(os.path.join(target_dir, "ic_launcher_round.png"), "PNG")
                
        print("✅ Iconos actualizados en todas las densidades.")
    except Exception as e:
        print(f"❌ Error al procesar el icono: {e}")

if __name__ == "__main__":
    resize_icon("controldroid_icon.jpg")
