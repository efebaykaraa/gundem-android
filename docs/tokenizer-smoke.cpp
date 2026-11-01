#include "onnx_tokens.h"
#include "llama.h"
#include <iostream>
#include <iterator>
int main(int argc,char **argv) {
    if(argc!=2) return 2;
    try {
        llama_backend_init();OnnxTokens tokenizer;tokenizer.load(argv[1]);
        std::string text((std::istreambuf_iterator<char>(std::cin)),std::istreambuf_iterator<char>());
        auto tokens=tokenizer.encode(text);std::cout<<"[";
        for(size_t i=0;i<tokens.size();++i) {if(i)std::cout<<",";std::cout<<tokens[i];}
        std::cout<<"]\n";
    } catch(const std::exception &e) {std::cerr<<e.what()<<std::endl;return 1;}
}
