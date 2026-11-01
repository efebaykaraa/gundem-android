#pragma once
#include <string>
#include <vector>
#include <cstdint>
struct llama_model;
struct llama_sampler;
class OnnxTokens {
    llama_model *model=nullptr;
    llama_sampler *sampler=nullptr;
public:
    ~OnnxTokens();
    void load(const std::string &path);
    void close();
    std::vector<int32_t> encode(const std::string &text) const;
    std::string decode(const std::vector<int32_t> &tokens) const;
    void grammar(const std::string &source);
    int sample(const float *logits,int count);
    bool end(int token) const;
};
