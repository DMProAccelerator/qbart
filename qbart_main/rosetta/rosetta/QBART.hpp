
  #ifndef QBART_H
  #define QBART_H
  #include "wrapperregdriver.h"
  #include <map>
  #include <string>
  #include <vector>

  using namespace std;
  class QBART {
  public:
    QBART(WrapperRegDriver * platform) {
      m_platform = platform;
      attach();
    }
    ~QBART() {
      detach();
    }

      void set_btn(AccelReg value) {writeReg(1, value);} 
  void set_conv(AccelReg value) {writeReg(2, value);} 
  AccelReg get_done() {return readReg(3);} 
  void set_fc(AccelReg value) {writeReg(4, value);} 
  void set_filterAddr(AccelDblReg value) { writeReg(5, (AccelReg)(value >> 32)); writeReg(6, (AccelReg)(value & 0xffffffff)); }
  void set_filtersAreSigned(AccelReg value) {writeReg(7, value);} 
  void set_filtersNumBits(AccelReg value) {writeReg(8, value);} 
  AccelReg get_finishedSlidingWindow() {return readReg(9);} 
  void set_imageAddr(AccelDblReg value) { writeReg(10, (AccelReg)(value >> 32)); writeReg(11, (AccelReg)(value & 0xffffffff)); }
  void set_imageHeight(AccelReg value) {writeReg(12, value);} 
  void set_imageIsSigned(AccelReg value) {writeReg(13, value);} 
  void set_imageNumBits(AccelReg value) {writeReg(14, value);} 
  void set_imageNumChannels(AccelReg value) {writeReg(15, value);} 
  void set_imageWidth(AccelReg value) {writeReg(16, value);} 
  AccelReg get_led() {return readReg(17);} 
  void set_lhs_addr(AccelDblReg value) { writeReg(18, (AccelReg)(value >> 32)); writeReg(19, (AccelReg)(value & 0xffffffff)); }
  void set_lhs_bits(AccelReg value) {writeReg(20, value);} 
  void set_lhs_cols(AccelReg value) {writeReg(21, value);} 
  void set_lhs_issigned(AccelReg value) {writeReg(22, value);} 
  void set_lhs_rows(AccelReg value) {writeReg(23, value);} 
  void set_numOutputChannels(AccelReg value) {writeReg(24, value);} 
  void set_num_chn(AccelReg value) {writeReg(25, value);} 
  void set_outputAddr(AccelDblReg value) { writeReg(26, (AccelReg)(value >> 32)); writeReg(27, (AccelReg)(value & 0xffffffff)); }
  void set_res_addr(AccelDblReg value) { writeReg(28, (AccelReg)(value >> 32)); writeReg(29, (AccelReg)(value & 0xffffffff)); }
  void set_rhs_addr(AccelDblReg value) { writeReg(30, (AccelReg)(value >> 32)); writeReg(31, (AccelReg)(value & 0xffffffff)); }
  void set_rhs_bits(AccelReg value) {writeReg(32, value);} 
  void set_rhs_cols(AccelReg value) {writeReg(33, value);} 
  void set_rhs_issigned(AccelReg value) {writeReg(34, value);} 
  void set_rhs_rows(AccelReg value) {writeReg(35, value);} 
  AccelReg get_signature() {return readReg(0);} 
  AccelReg get_sliderWaiting() {return readReg(36);} 
  void set_start(AccelReg value) {writeReg(37, value);} 
  void set_strideExponent(AccelReg value) {writeReg(38, value);} 
  void set_sw(AccelReg value) {writeReg(39, value);} 
  void set_tempAddr(AccelDblReg value) { writeReg(40, (AccelReg)(value >> 32)); writeReg(41, (AccelReg)(value & 0xffffffff)); }
  void set_thresh(AccelReg value) {writeReg(42, value);} 
  AccelReg get_tx() {return readReg(43);} 
  void set_uart(AccelReg value) {writeReg(44, value);} 
  void set_uart_data(AccelReg value) {writeReg(45, value);} 
  void set_windowSize(AccelReg value) {writeReg(46, value);} 


    map<string, vector<unsigned int>> getStatusRegs() {
      map<string, vector<unsigned int>> ret = { {"done", {3}} ,  {"finishedSlidingWindow", {9}} ,  {"led", {17}} ,  {"signature", {0}} ,  {"sliderWaiting", {36}} ,  {"tx", {43}} };
      return ret;
    }

    AccelReg readStatusReg(string regName) {
      map<string, vector<unsigned int>> statRegMap = getStatusRegs();
      if(statRegMap[regName].size() != 1) throw ">32 bit status regs are not yet supported from readStatusReg";
      return readReg(statRegMap[regName][0]);
    }

  protected:
    WrapperRegDriver * m_platform;
    AccelReg readReg(unsigned int i) {return m_platform->readReg(i);}
    void writeReg(unsigned int i, AccelReg v) {m_platform->writeReg(i,v);}
    void attach() {m_platform->attach("QBART");}
    void detach() {m_platform->detach();}
  };
  #endif
      