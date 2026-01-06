module DelayedAdder(clock, value_1, value_2, out);
  input clock;

  input[7:0] value_1;
  input[7:0] value_2;
  output[7:0] out;

  reg[7:0] flop1;
  reg[7:0] flop2;

  reg[7:0] flop3;
  reg[7:0] flop4;

  always @ (posedge clock)
      begin
        flop1 <= value_1;
        flop2 <= value_2;
        flop3 <= flop1;
        flop4 <= flop2;
      end

  assign out = flop4 & flop3;
endmodule
