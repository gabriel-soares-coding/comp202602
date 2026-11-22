program Expressoes
{ precedencia, parenteses, menos unario e numeros reais }
real: x, y, r;
x := 2.5;
y := 4;
r := -x + y * (3 - 1) / 2;
write(r);
if r >= 1.5 then
  write(x);
else
  write(y);
$
.
