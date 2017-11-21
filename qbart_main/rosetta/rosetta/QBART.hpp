
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

      void set_baseAddrRead(AccelDblReg value) { writeReg(1, (AccelReg)(value >> 32)); writeReg(2, (AccelReg)(value & 0xffffffff)); }
  void set_baseAddrWrite(AccelDblReg value) { writeReg(3, (AccelReg)(value >> 32)); writeReg(4, (AccelReg)(value & 0xffffffff)); }
  void set_btn(AccelReg value) {writeReg(5, value);} 
  void set_byteCount(AccelReg value) {writeReg(6, value);} 
  void set_byteCountReader(AccelReg value) {writeReg(7, value);} 
  void set_byteCountWriter(AccelReg value) {writeReg(8, value);} 
  void set_conv(AccelReg value) {writeReg(9, value);} 
  AccelReg get_done() {return readReg(10);} 
  void set_elemCount(AccelReg value) {writeReg(11, value);} 
  void set_fc(AccelReg value) {writeReg(12, value);} 
  void set_filterAddr(AccelDblReg value) { writeReg(13, (AccelReg)(value >> 32)); writeReg(14, (AccelReg)(value & 0xffffffff)); }
  void set_filtersAreSigned(AccelReg value) {writeReg(15, value);} 
  void set_filtersNumBits(AccelReg value) {writeReg(16, value);} 
  AccelReg get_finishedSlidingWindow() {return readReg(17);} 
  void set_imageAddr(AccelDblReg value) { writeReg(18, (AccelReg)(value >> 32)); writeReg(19, (AccelReg)(value & 0xffffffff)); }
  void set_imageHeight(AccelReg value) {writeReg(20, value);} 
  void set_imageIsSigned(AccelReg value) {writeReg(21, value);} 
  void set_imageNumBits(AccelReg value) {writeReg(22, value);} 
  void set_imageNumChannels(AccelReg value) {writeReg(23, value);} 
  void set_imageWidth(AccelReg value) {writeReg(24, value);} 
  AccelReg get_led() {return readReg(25);} 
  void set_lhs_addr(AccelDblReg value) { writeReg(26, (AccelReg)(value >> 32)); writeReg(27, (AccelReg)(value & 0xffffffff)); }
  void set_lhs_bits(AccelReg value) {writeReg(28, value);} 
  void set_lhs_cols(AccelReg value) {writeReg(29, value);} 
  void set_lhs_issigned(AccelReg value) {writeReg(30, value);} 
  void set_lhs_rows(AccelReg value) {writeReg(31, value);} 
  void set_numOutputChannels(AccelReg value) {writeReg(32, value);} 
  void set_num_chn(AccelReg value) {writeReg(33, value);} 
  void set_outputAddr(AccelDblReg value) { writeReg(34, (AccelReg)(value >> 32)); writeReg(35, (AccelReg)(value & 0xffffffff)); }
  void set_res_addr(AccelDblReg value) { writeReg(36, (AccelReg)(value >> 32)); writeReg(37, (AccelReg)(value & 0xffffffff)); }
  void set_rhs_addr(AccelDblReg value) { writeReg(38, (AccelReg)(value >> 32)); writeReg(39, (AccelReg)(value & 0xffffffff)); }
  void set_rhs_bits(AccelReg value) {writeReg(40, value);} 
  void set_rhs_cols(AccelReg value) {writeReg(41, value);} 
  void set_rhs_issigned(AccelReg value) {writeReg(42, value);} 
  void set_rhs_rows(AccelReg value) {writeReg(43, value);} 
  AccelReg get_signature() {return readReg(0);} 
  AccelReg get_sliderWaiting() {return readReg(44);} 
  void set_start(AccelReg value) {writeReg(45, value);} 
  void set_strideExponent(AccelReg value) {writeReg(46, value);} 
  void set_sw(AccelReg value) {writeReg(47, value);} 
  void set_tempAddr(AccelDblReg value) { writeReg(48, (AccelReg)(value >> 32)); writeReg(49, (AccelReg)(value & 0xffffffff)); }
  void set_thresh(AccelReg value) {writeReg(50, value);} 
  void set_threshCount(AccelReg value) {writeReg(51, value);} 
  AccelReg get_tx() {return readReg(52);} 
  void set_uart(AccelReg value) {writeReg(53, value);} 
  void set_uart_data(AccelReg value) {writeReg(54, value);} 
  void set_windowSize(AccelReg value) {writeReg(55, value);} 


    map<string, vector<unsigned int>> getStatusRegs() {
      map<string, vector<unsigned int>> ret = { {"done", {10}} ,  {"finishedSlidingWindow", {17}} ,  {"led", {25}} ,  {"signature", {0}} ,  {"sliderWaiting", {44}} ,  {"tx", {52}} };
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
      