program Teste
real: cont;
real: a,b,c;
cont := 10;
while cont > 0 do
  a := read(a);
  b := read(b);
  if a > b then
    c := a - b;
  else
    c := b - a;
  $
  write(c);
  cont := cont - 1;
$
write(c);
.
