# .latexmkrc — конфигурация latexmk для XeLaTeX + Biber
$pdf_mode = 5;          # использовать xelatex
$xelatex = 'xelatex -interaction=nonstopmode -halt-on-error %O %S';
$biber = 'biber %O %S';
$out_dir = 'build';
$clean_ext = 'bbl run.xml';
