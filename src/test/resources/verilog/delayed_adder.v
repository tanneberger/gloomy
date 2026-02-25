module DelayedAdder(clock, value_1, value_2, out);
  input clock;

  input[7:0] value_1;
  input[7:0] value_2;
  output[7:0] out;

  reg[7:0] flop1 = 8'b0;
  reg[7:0] flop2 = 8'b0;

  always @ (posedge clock)
      begin
        flop1 <= value_1;
        flop2 <= value_2;
      end

  assign out = flop1 & flop2;
endmodule
