#include <iostream>
#include "gemmbitserial.hpp"

/*
 * Steps from readme:
 * 1) Import "gemmbitserial.h"
 * 2) Instantiate a GEMMContext by using the allocateGEMMContext function.
 * 3) Import left-hand-side and right-hand-side matrices by calling gemmcontext.lhs.importRegular and gemmcontext.rhs.importRegular.
 * 4) Call the gemmBitSerial function with the gemmcontext as the argument.
 * 5) Done! You can now read out the result from gemmcontext.res
 * 6) Release the context by calling deallocGEMMContext.
 */


// TODO: To debug we use the generic architecture GEMMContext, but we should try out the ARM one when we deploy, there may be
// more performance benefits to harvest.

// Args, should be provided as input: uint64_t lhsRows, uint64_t depth, uint64_t rhsRows, uint64_t lhsBits, uint64_t rhsBits, bool lhsSigned, bool rhsSigned 
GEMMContext contextInstance = allocGEMMContext_generic()

/* First we define a gemmbitserial class we can call from Python. We could modify the original library, but let's not.*/
class gbs_multiply {
	public:
	       	/* Constructor */
		gbs_multiply(uint64_t lhsRows, uint64_t depth, 
					    uint64_t rhsRows, uint64_t lhsBits, 
					    uint64_t rhsBits, bool lhsSigned, 
					    bool rhsSigned);
		/* Destructor */
	        ~gbs_multiply(void);

		/* Matrix loading functions. */
		loadLhs(); // TODO: Implement this. Very uncertain about parameters (remember they are numpy arrays in python=
		loadRhs(); // TODO: Implement this

		/* Execution function */
		calculate(void);

	private:
		uint64_t lhsRows;
		uint64_t depth;
		uint64_t rhsRows;
		uint64_t lhsBits;
		uint64_t rhsBits;
		bool lhsSigned;
		bool rhsSigned;
		GEMMContext* multiplier_context;
}

/* Parameterized constructor for gbs_multiply class. */
gbs_multiply::gbs_multiply(uint64_t lhsRows, uint64_t depth, uint64_t rhsRows, uint64_t lhsBits, uint64_t rhsBits, bool lhsSigned, bool rhsSigned){
	this.lhsRows = lhsRows;
	this.depth = depth;
	this.rhsRows = rhsRows;
	this.lhsBits = lhsBits;
	this.rhsBits = rhsBits;
	this.lhsSigned = lhsSigned;
	this.rhsSigned = rhsSigned;

	this.multiplier_context = allocGEMMContext_generic(this.lhsRows, this.depth, this.rhsRows, this.lhsBits, this.rhsBits, this.lhsSigned, this.rhsSigned);
	
}

/* Destructor for gbs_multiply class. It is important to check for memory leaks, as we will likely construct
 * and destruct many instances of this class in applications. */
gbs_multiply::~gbs_multiply(void) {
	deallocGEMMContext(multiplier_context);
	delete multiplier_context;
}

/*
 * Perform the actual GEMMBitserial calculation.
 * */
gbs_multiply::calculate(void) {
	gemmBitSerial(this.multiplier_context);
}

/* 
 * CTypes only really works with C functions (C++ compiled ABI might differ depending on the compiler), so we do the following.
 * In effect, we are really wrapping C++ to C with extern C, and then from C to python using CTypes. Fun times!
 *
 * Credit: Florian Bösch, https://stackoverflow.com/questions/145270/calling-c-c-from-python
 */

extern "C" {	
	/* A simple hello world to test */
	void print(void)
	{
		std::cout << "Hello from c++ wrapper!\n" << std::endl;
	}

	/* Here we must import a gemmbitclass of sorts that can be called from python */
}
