:: script to sign msi file
:: 

"C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x86\signtool.exe" sign /tr http://timestamp.sectigo.com /td sha256 /fd sha256 /a "jDiskMark-0.5.1.msi"