$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'store'
function Draw-Shield($graphics, [single]$left, [single]$top, [single]$size) {
    $saved = $graphics.Save()
    $graphics.TranslateTransform($left, $top)
    $graphics.ScaleTransform($size / 108, $size / 108)
    $shield = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $shield.AddLines([System.Drawing.PointF[]]@([System.Drawing.PointF]::new(54,24),[System.Drawing.PointF]::new(30,34),[System.Drawing.PointF]::new(30,52)))
    $shield.AddBezier(30,52,30,67,40,77,54,84)
    $shield.AddBezier(54,84,68,77,78,67,78,52)
    $shield.AddLines([System.Drawing.PointF[]]@([System.Drawing.PointF]::new(78,52),[System.Drawing.PointF]::new(78,34),[System.Drawing.PointF]::new(54,24)))
    $shield.CloseFigure()
    $gradient = [System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Point]::new(30,24),[System.Drawing.Point]::new(78,84),[System.Drawing.ColorTranslator]::FromHtml('#82F2C5'),[System.Drawing.ColorTranslator]::FromHtml('#54B8D2'))
    $graphics.FillPath($gradient, $shield)
    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#123B36'),5)
    $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawLines($pen,[System.Drawing.PointF[]]@([System.Drawing.PointF]::new(42,53),[System.Drawing.PointF]::new(50,61),[System.Drawing.PointF]::new(66,44)))
    $pen.Dispose(); $gradient.Dispose(); $shield.Dispose()
    $graphics.Restore($saved)
}
foreach ($kind in @('icon','feature')) {
    $width = if ($kind -eq 'icon') { 512 } else { 1024 }
    $height = if ($kind -eq 'icon') { 512 } else { 500 }
    $bitmap = [System.Drawing.Bitmap]::new($width,$height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#0D2428'))
    if ($kind -eq 'icon') { Draw-Shield $graphics 0 0 512 }
    else {
        Draw-Shield $graphics 28 72 356
        $titleFont = [System.Drawing.Font]::new('Segoe UI',72,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
        $bodyFont = [System.Drawing.Font]::new('Segoe UI',29,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#F0F5F3'))
        $graphics.DrawString('still',$titleFont,$brush,420,130)
        $graphics.DrawString("Local DNS filtering.`nYour rules. Your control.",$bodyFont,$brush,425,245)
        $titleFont.Dispose(); $bodyFont.Dispose(); $brush.Dispose()
    }
    $bitmap.Save((Join-Path $assetDirectory "$kind.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}
