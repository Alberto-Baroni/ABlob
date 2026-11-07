Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$assetRoot = $PSScriptRoot
$output = Join-Path $assetRoot 'ABlob-immagine-in-primo-piano-1024x500.png'
$bitmap = [System.Drawing.Bitmap]::new(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

function RoundedPath([single]$x,[single]$y,[single]$w,[single]$h,[single]$radius) {
    $p = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $radius * 2
    $p.AddArc($x,$y,$d,$d,180,90)
    $p.AddArc(($x+$w-$d),$y,$d,$d,270,90)
    $p.AddArc(($x+$w-$d),($y+$h-$d),$d,$d,0,90)
    $p.AddArc($x,($y+$h-$d),$d,$d,90,90)
    $p.CloseFigure()
    return ,$p
}
function Text([string]$value,[single]$size,[string]$color,[single]$x,[single]$y,[bool]$bold = $false) {
    $family = if ($bold) {'Segoe UI Semibold'} else {'Segoe UI'}
    $font = [System.Drawing.Font]::new($family,$size,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.DrawString($value,$font,$brush,$x,$y)
    $brush.Dispose()
    $font.Dispose()
}

try {
    $background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Rectangle]::new(0,0,1024,500),
        [System.Drawing.ColorTranslator]::FromHtml('#0A1428'),
        [System.Drawing.ColorTranslator]::FromHtml('#201C46'),15.0)
    $g.FillRectangle($background,0,0,1024,500)
    $background.Dispose()

    $glow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(30,135,96,255))
    $g.FillEllipse($glow,530,-150,650,680)
    $glow.Dispose()
    $glow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(15,35,193,255))
    $g.FillEllipse($glow,760,230,440,440)
    $glow.Dispose()
    $ring = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(26,173,174,255),1.0)
    $g.DrawEllipse($ring,610,-65,560,560)
    $g.DrawEllipse($ring,660,-15,460,460)
    $ring.Dispose()

    $accent = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#9FAEFA'))
    $g.FillRectangle($accent,64,69,4,26)
    $accent.Dispose()
    Text 'ABlob' 28 '#FFFFFF' 82 62 $true
    Text 'Client per Azure Blob Storage' 17 '#AEBBD8' 83 100
    Text 'I tuoi file Azure.' 50 '#FFFFFF' 60 159 $true
    Text 'A portata di mano.' 50 '#C8C6FF' 60 219 $true
    Text 'Sfoglia, consulta e organizza' 22 '#C2CDE1' 64 306
    Text 'il tuo Blob Storage su Android.' 22 '#C2CDE1' 64 336
    Text 'File  /  Cartelle  /  Anteprime  /  Preferiti' 16 '#A3B3D1' 65 416

    $shadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(65,0,0,0))
    $shadowPath = RoundedPath 709 45 250 428 29
    $g.FillPath($shadow,$shadowPath)
    $shadow.Dispose(); $shadowPath.Dispose()
    $phone = RoundedPath 704 33 250 434 28
    $frame = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#111827'))
    $g.FillPath($frame,$phone)
    $frame.Dispose()
    $border = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(150,149,162,195),1.0)
    $g.DrawPath($border,$phone)
    $border.Dispose(); $phone.Dispose()

    $screen = RoundedPath 714 43 230 414 20
    $saved = $g.Save()
    $g.SetClip($screen)
    $image = [System.Drawing.Image]::FromFile((Join-Path $assetRoot 'screenshots/it-IT/01-file-e-cartelle.jpg'))
    $g.DrawImage($image,714,43,230,414)
    $image.Dispose()
    $g.Restore($saved)
    $screen.Dispose()
    $bitmap.Save($output,[System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $output
} finally {
    $g.Dispose()
    $bitmap.Dispose()
}
