$docxPath = 'c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\BAO_CAO_TOT_NGHIEP_OISHI_FOOD_CHUAN_TDC.docx'
Write-Host "Updating TOC and Word Cache for: $docxPath"

$w = New-Object -ComObject Word.Application
$w.Visible = $false
try {
    $doc = $w.Documents.Open($docxPath)
    Write-Host "Document opened. Total paragraphs: $($doc.Paragraphs.Count)"
    
    # Update all fields in document (including TOC)
    Write-Host "Updating fields and TOC..."
    foreach ($field in $doc.Fields) {
        $field.Update()
    }
    
    # Update TOCs explicitly
    foreach ($toc in $doc.TablesOfContents) {
        $toc.Update()
    }
    
    # Save the updated document
    $doc.Save()
    Write-Host "Document saved successfully with updated TOC!"
} catch {
    Write-Host "Error updating Word: $_"
} finally {
    if ($doc) { $doc.Close() }
    $w.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($w) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
Write-Host "Done!"
